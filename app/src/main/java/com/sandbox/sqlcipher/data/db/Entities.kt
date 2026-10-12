package com.sandbox.sqlcipher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index("phone")])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Номер собеседника; пустая строка, если система его не передала. */
    val phone: String,
    /** IDLE / OFFHOOK / RINGING */
    val event: String,
    /** Имя из таблицы users на момент события, если номер найден. */
    val name: String?,
)
