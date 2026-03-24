package com.yashraj.datastoreinspector.sample

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.yashraj.datastoreinspector.sample.databinding.ActivityMainBinding
import com.yashraj.datastoreinspector.sample.proto.UserPreferences
import com.yashraj.datastoreinspector.sample.proto.copy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
val Context.protoDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_prefs.pb",
    serializer = UserPreferencesSerializer
)

class MainActivity : AppCompatActivity() {

    private val PREFS_NAME = "user_shared_prefs"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Save default data initially
        saveToSharedPrefs()
        saveToPreferencesDataStore()
        saveToProtoDataStore()

        // Load saved data
        loadFromSharedPrefs()
        observePreferencesDataStore()
        observeProtoDataStore()
    }

    private fun saveToSharedPrefs() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString("username", "JohnDoe_SharedPrefs")
            .putString("email", "john.shared@example.com")
            .putString("number", "1234567890")
            .apply()
    }

    private fun loadFromSharedPrefs() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", "N/A")
        val email = sharedPreferences.getString("email", "N/A")
        val number = sharedPreferences.getString("number", "N/A")
        binding.tvSharedPrefs.text = "Username: $username\nEmail: $email\nNumber: $number"
    }

    private fun saveToPreferencesDataStore() {
        lifecycleScope.launch {
            val usernameKey = stringPreferencesKey("username")
            val emailKey = stringPreferencesKey("email")
            val numberKey = stringPreferencesKey("number")

            prefsDataStore.edit { prefs ->
                prefs[usernameKey] = "JohnDoe_PrefsDS"
                prefs[emailKey] = "john.prefs@example.com"
                prefs[numberKey] = "0987654321"
            }
        }
    }

    private fun observePreferencesDataStore() {
        lifecycleScope.launch {
            val usernameKey = stringPreferencesKey("username")
            val emailKey = stringPreferencesKey("email")
            val numberKey = stringPreferencesKey("number")

            prefsDataStore.data.collectLatest { prefs ->
                val username = prefs[usernameKey] ?: "N/A"
                val email = prefs[emailKey] ?: "N/A"
                val number = prefs[numberKey] ?: "N/A"

                binding.tvPrefsDataStore.text = "Username: $username\nEmail: $email\nNumber: $number"
            }
        }
    }

    private fun saveToProtoDataStore() {
        lifecycleScope.launch {
            protoDataStore.updateData { currentPrefs ->
                currentPrefs.copy {
                    username = "JohnDoe_ProtoDS"
                    email = "john.proto@example.com"
                    number = "9999999999"
                }
            }
        }
    }

    private fun observeProtoDataStore() {
        lifecycleScope.launch {
            protoDataStore.data.collectLatest { userPrefs ->
                binding.tvProtoDataStore.text = "Username: ${userPrefs.username}\nEmail: ${userPrefs.email}\nNumber: ${userPrefs.number}"
            }
        }
    }
}