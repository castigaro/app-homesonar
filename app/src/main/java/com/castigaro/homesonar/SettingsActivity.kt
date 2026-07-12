package com.castigaro.homesonar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.castigaro.homesonar.api.ModelPricing
import com.castigaro.homesonar.data.AppSettings
import com.castigaro.homesonar.databinding.ActivitySettingsBinding
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

/**
 * Einstellungen rund um die KI-Anbindung. Die Oberfläche ist auf den
 * Normalfall "ein Key" zugeschnitten:
 *
 * - Kein Key gespeichert: Anbieter wählen (Toggle), nur dessen Sektion ist
 *   sichtbar, mit Link zur Key-Registrierung.
 * - Genau ein Key: nur diese Sektion; der zweite Anbieter lässt sich über
 *   einen Button einblenden.
 * - Beide Keys: beide Sektionen plus Toggle für den primären Anbieter —
 *   nur dieser wird genutzt, der andere ruht (kein Fallback).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    /** Vom Nutzer eingeblendete Zweit-Sektion (Zustand B), nicht persistent. */
    private var showSecondSection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Felder mit gespeicherten Werten vorbelegen.
        binding.inputKeyAnthropic.setText(AppSettings.getKey(this, AppSettings.PROVIDER_ANTHROPIC))
        binding.inputModelAnthropic.setText(AppSettings.getModel(this, AppSettings.PROVIDER_ANTHROPIC))
        binding.switchEnabledAnthropic.isChecked = AppSettings.isEnabled(this, AppSettings.PROVIDER_ANTHROPIC)
        binding.inputKeyOpenai.setText(AppSettings.getKey(this, AppSettings.PROVIDER_OPENAI))
        binding.inputModelOpenai.setText(AppSettings.getModel(this, AppSettings.PROVIDER_OPENAI))
        binding.switchEnabledOpenai.isChecked = AppSettings.isEnabled(this, AppSettings.PROVIDER_OPENAI)

        binding.inputModelAnthropic.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ModelPricing.SUGGESTED_ANTHROPIC)
        )
        binding.inputModelOpenai.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ModelPricing.SUGGESTED_OPENAI)
        )

        if (AppSettings.getPrimaryProvider(this) == AppSettings.PROVIDER_OPENAI) {
            binding.providerToggle.check(binding.toggleOpenai.id)
        } else {
            binding.providerToggle.check(binding.toggleAnthropic.id)
        }

        // Im Zustand A (kein Key) schaltet der Toggle die sichtbare Sektion um.
        binding.providerToggle.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) refreshUi()
        }

        // Registrierungs-Link nur zeigen, solange das Key-Feld leer ist.
        binding.inputKeyAnthropic.afterTextChanged { refreshRegisterLinks() }
        binding.inputKeyOpenai.afterTextChanged { refreshRegisterLinks() }
        binding.registerLinkAnthropic.setOnClickListener { openUrl(URL_REGISTER_ANTHROPIC) }
        binding.registerLinkOpenai.setOnClickListener { openUrl(URL_REGISTER_OPENAI) }

        binding.buttonAddSecond.setOnClickListener {
            showSecondSection = true
            refreshUi()
        }

        binding.buttonResetCostAnthropic.setOnClickListener { resetCost(AppSettings.PROVIDER_ANTHROPIC) }
        binding.buttonResetCostOpenai.setOnClickListener { resetCost(AppSettings.PROVIDER_OPENAI) }
        binding.buttonDeleteAnthropic.setOnClickListener { deleteKey(AppSettings.PROVIDER_ANTHROPIC) }
        binding.buttonDeleteOpenai.setOnClickListener { deleteKey(AppSettings.PROVIDER_OPENAI) }

        binding.buttonSaveSettings.setOnClickListener { saveSettings() }

        refreshUi()
    }

    // ---- Zustandslogik ----

    private fun refreshUi() {
        val hasAnthropic = AppSettings.getKey(this, AppSettings.PROVIDER_ANTHROPIC).isNotBlank()
        val hasOpenai = AppSettings.getKey(this, AppSettings.PROVIDER_OPENAI).isNotBlank()

        val toggledOpenai = binding.providerToggle.checkedButtonId == binding.toggleOpenai.id

        when {
            // Zustand A: kein Key — Anbieter wählen, nur diese Sektion zeigen.
            !hasAnthropic && !hasOpenai -> {
                binding.providerToggleLabel.text = getString(R.string.provider_choose_label)
                binding.providerToggleLabel.visibility = View.VISIBLE
                binding.providerToggle.visibility = View.VISIBLE
                binding.sectionAnthropic.visibility = if (toggledOpenai) View.GONE else View.VISIBLE
                binding.sectionOpenai.visibility = if (toggledOpenai) View.VISIBLE else View.GONE
                binding.buttonAddSecond.visibility = View.GONE
                showSecondSection = false
            }
            // Zustand C: beide Keys — Toggle wählt den primären Anbieter.
            hasAnthropic && hasOpenai -> {
                binding.providerToggleLabel.text = getString(R.string.primary_provider_label)
                binding.providerToggleLabel.visibility = View.VISIBLE
                binding.providerToggle.visibility = View.VISIBLE
                binding.sectionAnthropic.visibility = View.VISIBLE
                binding.sectionOpenai.visibility = View.VISIBLE
                binding.buttonAddSecond.visibility = View.GONE
            }
            // Zustand B: genau ein Key — nur dessen Sektion, zweite auf Wunsch.
            else -> {
                binding.providerToggleLabel.visibility = View.GONE
                binding.providerToggle.visibility = View.GONE
                binding.sectionAnthropic.visibility =
                    if (hasAnthropic || showSecondSection) View.VISIBLE else View.GONE
                binding.sectionOpenai.visibility =
                    if (hasOpenai || showSecondSection) View.VISIBLE else View.GONE
                binding.buttonAddSecond.visibility = if (showSecondSection) View.GONE else View.VISIBLE
                binding.buttonAddSecond.text = getString(
                    R.string.add_second_provider,
                    if (hasAnthropic) getString(R.string.provider_openai) else getString(R.string.provider_anthropic),
                )
            }
        }

        refreshRegisterLinks()
        refreshCosts()
    }

    private fun refreshRegisterLinks() {
        binding.registerLinkAnthropic.visibility =
            if (binding.inputKeyAnthropic.text.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.registerLinkOpenai.visibility =
            if (binding.inputKeyOpenai.text.isNullOrBlank()) View.VISIBLE else View.GONE
    }

    private fun refreshCosts() {
        binding.costAnthropic.text = costText(AppSettings.PROVIDER_ANTHROPIC)
        binding.costOpenai.text = costText(AppSettings.PROVIDER_OPENAI)
    }

    private fun costText(provider: String): String {
        val usd = AppSettings.getCostMicros(this, provider) / 1_000_000.0
        return getString(R.string.api_costs, String.format(Locale.GERMANY, "%.4f", usd))
    }

    // ---- Aktionen ----

    private fun saveSettings() {
        val anthropicKey = binding.inputKeyAnthropic.text.toString().trim()
        val openaiKey = binding.inputKeyOpenai.text.toString().trim()

        // Primär ist der Toggle-Anbieter; hat der aber (nach diesem Speichern)
        // keinen Key und der andere schon, gewinnt der mit Key.
        val toggled = if (binding.providerToggle.checkedButtonId == binding.toggleOpenai.id) {
            AppSettings.PROVIDER_OPENAI
        } else {
            AppSettings.PROVIDER_ANTHROPIC
        }
        val toggledHasKey = if (toggled == AppSettings.PROVIDER_OPENAI) openaiKey else anthropicKey
        val otherHasKey = if (toggled == AppSettings.PROVIDER_OPENAI) anthropicKey else openaiKey
        val primary = if (toggledHasKey.isBlank() && otherHasKey.isNotBlank()) {
            AppSettings.otherProvider(toggled)
        } else {
            toggled
        }

        AppSettings.save(
            this,
            primaryProvider = primary,
            anthropicKey = anthropicKey,
            anthropicModel = binding.inputModelAnthropic.text.toString(),
            anthropicEnabled = binding.switchEnabledAnthropic.isChecked,
            openaiKey = openaiKey,
            openaiModel = binding.inputModelOpenai.text.toString(),
            openaiEnabled = binding.switchEnabledOpenai.isChecked,
        )
        refreshUi()
        Snackbar.make(binding.root, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun deleteKey(provider: String) {
        AppSettings.deleteKey(this, provider)
        if (provider == AppSettings.PROVIDER_ANTHROPIC) {
            binding.inputKeyAnthropic.setText("")
        } else {
            binding.inputKeyOpenai.setText("")
        }
        // Toggle auf den neuen primären Anbieter nachziehen.
        if (AppSettings.getPrimaryProvider(this) == AppSettings.PROVIDER_OPENAI) {
            binding.providerToggle.check(binding.toggleOpenai.id)
        } else {
            binding.providerToggle.check(binding.toggleAnthropic.id)
        }
        showSecondSection = false
        refreshUi()
        Snackbar.make(binding.root, R.string.key_deleted, Snackbar.LENGTH_SHORT).show()
    }

    private fun resetCost(provider: String) {
        AppSettings.resetCost(this, provider)
        refreshCosts()
        Snackbar.make(binding.root, R.string.api_costs_reset_done, Snackbar.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val URL_REGISTER_ANTHROPIC = "https://console.anthropic.com/settings/keys"
        private const val URL_REGISTER_OPENAI = "https://platform.openai.com/api-keys"
    }
}

/** Kleiner Helfer, damit TextWatcher nicht drei leere Methoden braucht. */
private fun android.widget.EditText.afterTextChanged(action: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = action()
    })
}
