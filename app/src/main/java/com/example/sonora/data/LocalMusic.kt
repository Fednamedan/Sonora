package com.example.sonora.data

import android.content.Context
import android.provider.MediaStore

/** Музыка, уже лежащая на телефоне (любые mp3, m4a, flac из системной медиатеки). */
object LocalMusic {
    const val PREFIX = "lc:"

    fun scan(context: Context): List<Track> {
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val artist = c.getString(iArtist).orEmpty().takeUnless { it == "<unknown>" } ?: ""
                out.add(
                    Track(
                        id = "$PREFIX${c.getLong(iId)}",
                        title = c.getString(iTitle) ?: "Без названия",
                        artist = artist,
                        durationSec = (c.getLong(iDur) / 1000).toInt(),
                        genre = "",
                        tags = emptyList(),
                        artworkUrl = "content://media/external/audio/albumart/${c.getLong(iAlbum)}",
                        permalink = null,
                    )
                )
            }
        }
        return out
    }
}
