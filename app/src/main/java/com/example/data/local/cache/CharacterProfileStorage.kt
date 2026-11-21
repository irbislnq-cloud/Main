package com.example.data.local.cache

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val bio: String,
    val personalityTraits: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun traitList(): List<String> =
        personalityTraits.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

@Dao
interface CharacterProfileDao {
    @Query("SELECT * FROM character_profiles ORDER BY updatedAtMs DESC, id DESC")
    fun observeAllProfiles(): Flow<List<CharacterProfileEntity>>

    @Query(
        """
        SELECT * FROM character_profiles
        WHERE name LIKE '%' || :query || '%'
           OR bio LIKE '%' || :query || '%'
           OR personalityTraits LIKE '%' || :query || '%'
        ORDER BY updatedAtMs DESC, id DESC
        """
    )
    fun searchProfiles(query: String): Flow<List<CharacterProfileEntity>>

    @Query("SELECT * FROM character_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): CharacterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CharacterProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: CharacterProfileEntity)

    @Query("DELETE FROM character_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)
}

class CharacterProfileRepository(
    private val dao: CharacterProfileDao
) {
    val allProfiles: Flow<List<CharacterProfileEntity>> = dao.observeAllProfiles()

    fun searchProfiles(query: String): Flow<List<CharacterProfileEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) allProfiles else dao.searchProfiles(trimmed)
    }

    suspend fun getById(id: Long): CharacterProfileEntity? = dao.getProfileById(id)

    suspend fun saveProfile(
        id: Long = 0L,
        name: String,
        bio: String,
        personalityTraits: String
    ): Long {
        val now = System.currentTimeMillis()
        val existing = if (id > 0L) dao.getProfileById(id) else null
        val entity = CharacterProfileEntity(
            id = id,
            name = name.trim(),
            bio = bio.trim(),
            personalityTraits = personalityTraits.trim(),
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now
        )
        return dao.upsertProfile(entity)
    }

    suspend fun deleteProfile(id: Long) {
        dao.deleteProfileById(id)
    }
}
