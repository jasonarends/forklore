package com.jasonarends.forklore.testing

import com.jasonarends.forklore.data.db.PersonDao
import com.jasonarends.forklore.data.db.PersonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PersonDao] double for repository and ViewModel unit tests, so those tests don't need a
 * mocking framework or a real Room database. Doesn't enforce the unique index on
 * [PersonEntity.normalizedName] itself — that constraint is asserted against real SQLite in
 * `AcceptanceSpecTest`, which is what guards the schema. This fake exists so `PersonRepository` and
 * `PeopleViewModel` can be tested for the behaviour they own on top of it.
 */
class FakePersonDao : PersonDao {
  private val people = MutableStateFlow<Map<String, PersonEntity>>(emptyMap())

  override suspend fun insert(person: PersonEntity) {
    people.update { it + (person.id to person) }
  }

  override suspend fun update(person: PersonEntity) {
    people.update { it + (person.id to person) }
  }

  override fun observeAll(): Flow<List<PersonEntity>> = people.map { snapshot ->
    snapshot.values.filter { it.deletedAt == null }.sortedBy { it.name }
  }

  override fun observeHousehold(): Flow<List<PersonEntity>> = people.map { snapshot ->
    snapshot.values.filter { it.deletedAt == null && it.isHouseholdMember }.sortedBy { it.name }
  }

  override suspend fun byId(id: String): PersonEntity? = people.value[id]

  override suspend fun byNormalizedName(normalizedName: String): PersonEntity? =
    people.value.values.firstOrNull { it.normalizedName == normalizedName && it.deletedAt == null }
}
