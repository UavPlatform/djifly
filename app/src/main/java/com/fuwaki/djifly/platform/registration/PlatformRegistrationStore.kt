package com.fuwaki.djifly.platform.registration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.platformRegistrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "platform_registration"
)

@Singleton
class PlatformRegistrationStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    val latestRegistration: Flow<RegisteredDroneSnapshot?> = appContext.platformRegistrationDataStore.data.map { preferences ->
        val serialNumber = preferences[SERIAL_NUMBER]
        val droneName = preferences[DRONE_NAME]
        val controllerModel = preferences[CONTROLLER_MODEL]
        val registeredAtMillis = preferences[REGISTERED_AT_MILLIS]
        if (serialNumber.isNullOrBlank() || droneName.isNullOrBlank() || controllerModel.isNullOrBlank() || registeredAtMillis == null) {
            null
        } else {
            RegisteredDroneSnapshot(
                serialNumber = serialNumber,
                droneId = preferences[DRONE_ID],
                droneName = droneName,
                controllerModel = controllerModel,
                createdOnServer = preferences[CREATED_ON_SERVER] ?: false,
                lastRegisteredAtMillis = registeredAtMillis
            )
        }
    }

    suspend fun save(snapshot: RegisteredDroneSnapshot) {
        appContext.platformRegistrationDataStore.edit { preferences ->
            preferences[SERIAL_NUMBER] = snapshot.serialNumber
            preferences[DRONE_NAME] = snapshot.droneName
            preferences[CONTROLLER_MODEL] = snapshot.controllerModel
            preferences[REGISTERED_AT_MILLIS] = snapshot.lastRegisteredAtMillis
            preferences[CREATED_ON_SERVER] = snapshot.createdOnServer
            snapshot.droneId?.let { preferences[DRONE_ID] = it } ?: preferences.remove(DRONE_ID)
        }
    }

    companion object {
        private val SERIAL_NUMBER = stringPreferencesKey("serial_number")
        private val DRONE_ID = longPreferencesKey("drone_id")
        private val DRONE_NAME = stringPreferencesKey("drone_name")
        private val CONTROLLER_MODEL = stringPreferencesKey("controller_model")
        private val CREATED_ON_SERVER = booleanPreferencesKey("created_on_server")
        private val REGISTERED_AT_MILLIS = longPreferencesKey("registered_at_millis")
    }
}
