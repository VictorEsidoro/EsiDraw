package io.github.victoresidoro.esidraw

import android.app.Application
import io.github.victoresidoro.esidraw.data.db.EsiDrawDatabase
import io.github.victoresidoro.esidraw.data.repo.RoomEsiDrawRepository
import io.github.victoresidoro.esidraw.domain.repository.EsiDrawRepository

class EsiDrawApplication : Application() {

    /** Injeção manual: um único repositório para o app inteiro. */
    val repository: EsiDrawRepository by lazy { RoomEsiDrawRepository(EsiDrawDatabase.create(this)) }
}
