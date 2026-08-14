package org.whaleharness.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppRepository(context: Context) {
    private val preferences = context.getSharedPreferences("whale_harness", Context.MODE_PRIVATE)
    private val apiKeyStore = EncryptedStringStore("whale_harness_api_key")
    private val remoteTokenStore = EncryptedStringStore("whale_harness_remote_token")

    fun loadConfig(): ApiConfig = ApiConfig(
        baseUrl = preferences.getString("base_url", null) ?: "https://api.deepseek.com",
        model = preferences.getString("model", null) ?: "deepseek-chat",
        apiKey = preferences.getString("api_key", null)?.let(apiKeyStore::decrypt).orEmpty(),
    )

    fun saveConfig(config: ApiConfig) {
        EndpointResolver.chatCompletions(config.baseUrl)
        require(config.model.isNotBlank()) { "请填写模型名称" }
        require(config.apiKey.isNotBlank()) { "请填写 API Key" }
        preferences.edit {
            putString("base_url", config.baseUrl.trim())
            putString("model", config.model.trim())
            putString("api_key", apiKeyStore.encrypt(config.apiKey.trim()))
        }
    }

    fun loadRemoteConfig(): RemoteHarnessConfig? {
        val baseUrl = preferences.getString("remote_base_url", null).orEmpty()
        val encryptedToken = preferences.getString("remote_token", null) ?: return null
        val token = remoteTokenStore.decrypt(encryptedToken)
        if (baseUrl.isBlank() || token.isBlank()) return null
        return runCatching { RemoteHarnessConfig.fromManualEntry(baseUrl, token) }.getOrNull()
    }

    fun saveRemoteConfig(config: RemoteHarnessConfig) {
        preferences.edit {
            putString("remote_base_url", config.baseUrl)
            putString("remote_token", remoteTokenStore.encrypt(config.token))
        }
    }

    fun clearRemoteConfig() {
        preferences.edit {
            remove("remote_base_url")
            remove("remote_token")
        }
        remoteTokenStore.clear()
    }

    fun loadSkills(): List<Skill> {
        val raw = preferences.getString("skills", null) ?: return listOf(defaultSkill())
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Skill(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    prompt = item.getString("prompt"),
                    enabled = item.optBoolean("enabled", true),
                )
            }
        }.getOrElse { listOf(defaultSkill()) }
    }

    fun saveSkills(skills: List<Skill>) {
        val array = JSONArray()
        skills.forEach { skill ->
            array.put(JSONObject().apply {
                put("id", skill.id)
                put("name", skill.name)
                put("prompt", skill.prompt)
                put("enabled", skill.enabled)
            })
        }
        preferences.edit { putString("skills", array.toString()) }
    }

    fun importedSkill(fileName: String, content: String): Skill {
        val title = content.lineSequence()
            .firstOrNull { it.trimStart().startsWith("#") }
            ?.trim()?.trimStart('#')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.').ifBlank { "导入的 Skill" }
        return Skill(id = UUID.randomUUID().toString(), name = title, prompt = content.trim())
    }

    private fun defaultSkill() = Skill(
        name = "清晰回答",
        prompt = "先给结论，再说明依据。区分已经确认、合理推断和仍需验证的内容，不编造结果。",
    )
}

private class EncryptedStringStore(private val alias: String) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        return "$iv.$encrypted"
    }

    fun decrypt(value: String): String = runCatching {
        val (iv, encrypted) = value.split('.', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
    }.getOrDefault("")

    fun clear() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
