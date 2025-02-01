package com.example.myapplication

import android.util.Log

class PhotoDatabaseHelper(context: android.content.Context) : android.database.sqlite.SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT, file_path TEXT, timestamp TEXT, uploaded INTEGER DEFAULT 0)")
        Log.d("Database", "Таблица photos создана")
    }

    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS photos")
        onCreate(db)
    }

    fun addPhoto(filePath: String) {
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put("file_path", filePath)
            put("timestamp", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
        }
        val rowId = db.insert("photos", null, values)
        if (rowId != -1L) {
            Log.d("Database", "Фото успешно добавлено в базу: $filePath")
        } else {
            Log.e("Database", "Ошибка при добавлении фото в базу: $filePath")
        }
        db.close()
    }

    fun getAllPhotos(): List<String> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT file_path FROM photos", null)
        val photos = mutableListOf<String>()
        while (cursor.moveToNext()) {
            photos.add(cursor.getString(0))
        }
        cursor.close()
        db.close()
        return photos
    }

    companion object {
        private const val DATABASE_NAME = "photos.db"
        private const val DATABASE_VERSION = 1
    }
}
