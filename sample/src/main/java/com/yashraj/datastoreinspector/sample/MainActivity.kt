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
import kotlin.random.Random

val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
val Context.protoDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_prefs.pb",
    serializer = UserPreferencesSerializer
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        private val RANDOM_KEYS = listOf(
            "city", "country", "age", "nickname", "language",
            "theme", "timezone", "occupation", "website", "bio"
        )
        private val RANDOM_VALUES = listOf(
            "New York", "India", "42", "johnny", "English",
            "dark", "UTC+5:30", "Engineer", "example.com", "Hello world"
        )
    }

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

        binding.btnAddRandomField.setOnClickListener {
            addRandomField()
        }
    }

    private fun addRandomField() {
        val index = Random.nextInt(RANDOM_KEYS.size)
        val key = RANDOM_KEYS[index]
        val value = RANDOM_VALUES[index]

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(key, value).apply()
        loadFromSharedPrefs()

        lifecycleScope.launch {
            prefsDataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = value
            }
        }
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
        val text = sharedPreferences.all.entries
            .joinToString("\n") { (k, v) -> "$k: $v" }
        binding.tvSharedPrefs.text = text.ifEmpty { "No data" }
    }

    private fun saveToPreferencesDataStore() {
        lifecycleScope.launch {
            prefsDataStore.edit { prefs ->
                prefs[stringPreferencesKey("username")] = "JohnDoe_PrefsDS"
                prefs[stringPreferencesKey("email")] = "john.prefs@example.com"
                prefs[stringPreferencesKey("number")] = "0987654321"
            }
        }
    }

    private fun observePreferencesDataStore() {
        lifecycleScope.launch {
            prefsDataStore.data.collectLatest { prefs ->
                val text = prefs.asMap().entries
                    .joinToString("\n") { (k, v) -> "${k.name}: $v" }
                binding.tvPrefsDataStore.text = text.ifEmpty { "No data" }
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
                binding.tvProtoDataStore.text =
                    "Username: ${userPrefs.username}\nEmail: ${userPrefs.email}\nNumber: ${userPrefs.number}"
            }
        }
    }
}