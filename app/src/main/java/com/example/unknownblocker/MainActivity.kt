package com.example.unknownblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var toggle: Switch
    private lateinit var suppressVmSwitch: Switch
    private lateinit var notificationAccessButton: Button
    private lateinit var probeStatusText: TextView
    private lateinit var openProbeButton: Button
    private lateinit var clearProbeButton: Button
    private lateinit var statusText: TextView
    private lateinit var logHeader: TextView
    private lateinit var emptyLogText: TextView
    private lateinit var blockedListContainer: LinearLayout
    private lateinit var clearLogButton: Button
    private lateinit var refreshButton: Button
    private lateinit var areaCodeInput: EditText
    private lateinit var addAreaCodeButton: Button
    private lateinit var areaCodesEmpty: TextView
    private lateinit var areaCodesContainer: LinearLayout

    private val prefs by lazy { getSharedPreferences(BlockerSettings.PREFS_NAME, Context.MODE_PRIVATE) }
    private val dateFormat: DateFormat by lazy {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    private val requestCallScreeningLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
            if (!isCallScreeningRoleHeld()) {
                Toast.makeText(
                    this,
                    R.string.call_screening_not_granted,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.toggleSwitch)
        suppressVmSwitch = findViewById(R.id.suppressVmSwitch)
        notificationAccessButton = findViewById(R.id.notificationAccessButton)
        probeStatusText = findViewById(R.id.probeStatusText)
        openProbeButton = findViewById(R.id.openProbeButton)
        clearProbeButton = findViewById(R.id.clearProbeButton)
        statusText = findViewById(R.id.statusText)
        logHeader = findViewById(R.id.logHeader)
        emptyLogText = findViewById(R.id.emptyLogText)
        blockedListContainer = findViewById(R.id.blockedListContainer)
        clearLogButton = findViewById(R.id.clearLogButton)
        refreshButton = findViewById(R.id.refreshButton)
        areaCodeInput = findViewById(R.id.areaCodeInput)
        addAreaCodeButton = findViewById(R.id.addAreaCodeButton)
        areaCodesEmpty = findViewById(R.id.areaCodesEmpty)
        areaCodesContainer = findViewById(R.id.areaCodesContainer)

        toggle.isChecked = prefs.getBoolean(BlockerSettings.KEY_ENABLED, false)
        suppressVmSwitch.isChecked = BlockerSettings.isSuppressVmNotificationsEnabled(this)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            BlockerSettings.setBlockingEnabled(this, isChecked)
            if (isChecked) {
                requestPermissionsIfNeeded()
                requestCallScreeningRole()
            }
            refreshStatus()
        }

        suppressVmSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerSettings.setSuppressVmNotificationsEnabled(this, isChecked)
            if (isChecked && !BlockerSettings.isNotificationListenerEnabled(this)) {
                Toast.makeText(this, R.string.suppress_vm_needs_access, Toast.LENGTH_LONG).show()
                openNotificationAccessSettings()
            }
            refreshStatus()
        }

        notificationAccessButton.setOnClickListener {
            openNotificationAccessSettings()
        }

        openProbeButton.setOnClickListener { openProbeLogFile() }

        clearProbeButton.setOnClickListener {
            NotificationProbe.clear(this)
            refreshProbeStatus()
            Toast.makeText(this, R.string.probe_cleared, Toast.LENGTH_SHORT).show()
        }

        addAreaCodeButton.setOnClickListener { addAreaCodeFromInput() }
        areaCodeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                addAreaCodeFromInput()
                true
            } else {
                false
            }
        }

        clearLogButton.setOnClickListener {
            if (BlockLog.count(this) == 0) {
                Toast.makeText(this, R.string.log_already_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_log_title)
                .setMessage(R.string.clear_log_message)
                .setPositiveButton(R.string.clear) { _, _ ->
                    BlockLog.clear(this)
                    refreshLog()
                    Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refreshButton.setOnClickListener {
            refreshStatus()
            refreshLog()
            refreshAreaCodes()
            refreshProbeStatus()
            Toast.makeText(this, R.string.refreshed, Toast.LENGTH_SHORT).show()
        }

        if (toggle.isChecked) {
            requestPermissionsIfNeeded()
            requestCallScreeningRole()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync switch if user changed notification access in system settings
        suppressVmSwitch.setOnCheckedChangeListener(null)
        suppressVmSwitch.isChecked = BlockerSettings.isSuppressVmNotificationsEnabled(this)
        suppressVmSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerSettings.setSuppressVmNotificationsEnabled(this, isChecked)
            if (isChecked && !BlockerSettings.isNotificationListenerEnabled(this)) {
                Toast.makeText(this, R.string.suppress_vm_needs_access, Toast.LENGTH_LONG).show()
                openNotificationAccessSettings()
            }
            refreshStatus()
        }
        refreshStatus()
        refreshLog()
        refreshAreaCodes()
        refreshProbeStatus()
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(BlockerSettings.notificationListenerSettingsIntent())
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_notification_access, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openProbeLogFile() {
        if (!NotificationProbe.existsAndNonEmpty(this)) {
            Toast.makeText(this, R.string.probe_empty, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = NotificationProbe.logFile(this)
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(view, getString(R.string.open_probe_log)))
        } catch (_: Exception) {
            // Fallback: share sheet (works when no text viewer is registered)
            try {
                val file = NotificationProbe.logFile(this)
                val uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, NotificationProbe.LOG_FILE_NAME)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, getString(R.string.open_probe_log)))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.probe_open_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addAreaCodeFromInput() {
        val raw = areaCodeInput.text?.toString().orEmpty()
        when (val result = AreaCodeAllowlist.add(this, raw)) {
            is AreaCodeAllowlist.AddResult.Added -> {
                areaCodeInput.text?.clear()
                hideKeyboard()
                refreshAreaCodes()
                refreshStatus()
                Toast.makeText(
                    this,
                    getString(R.string.area_code_added, result.code),
                    Toast.LENGTH_SHORT
                ).show()
            }
            AreaCodeAllowlist.AddResult.Invalid -> {
                Toast.makeText(this, R.string.area_code_invalid, Toast.LENGTH_SHORT).show()
            }
            AreaCodeAllowlist.AddResult.Duplicate -> {
                val code = AreaCodeAllowlist.normalizeCode(raw) ?: raw
                Toast.makeText(
                    this,
                    getString(R.string.area_code_duplicate, code),
                    Toast.LENGTH_SHORT
                ).show()
            }
            AreaCodeAllowlist.AddResult.Full -> {
                Toast.makeText(this, R.string.area_code_full, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshAreaCodes() {
        val codes = AreaCodeAllowlist.load(this)
        areaCodesContainer.removeAllViews()
        if (codes.isEmpty()) {
            areaCodesEmpty.visibility = View.VISIBLE
            return
        }
        areaCodesEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        for (code in codes) {
            val row = inflater.inflate(R.layout.item_area_code, areaCodesContainer, false)
            row.findViewById<TextView>(R.id.areaCodeLabel).text =
                getString(R.string.area_code_row, code)
            row.findViewById<Button>(R.id.removeAreaCodeButton).setOnClickListener {
                AreaCodeAllowlist.remove(this, code)
                refreshAreaCodes()
                refreshStatus()
                Toast.makeText(
                    this,
                    getString(R.string.area_code_removed, code),
                    Toast.LENGTH_SHORT
                ).show()
            }
            areaCodesContainer.addView(row)
        }
    }

    private fun refreshStatus() {
        val enabled = prefs.getBoolean(BlockerSettings.KEY_ENABLED, false)
        val roleHeld = isCallScreeningRoleHeld()
        val contactsOk = hasPermission(Manifest.permission.READ_CONTACTS)
        val smsOk = hasPermission(Manifest.permission.RECEIVE_SMS)
        val areaCodes = AreaCodeAllowlist.load(this)
        val suppressVm = BlockerSettings.isSuppressVmNotificationsEnabled(this)
        val notifAccess = BlockerSettings.isNotificationListenerEnabled(this)

        val lines = mutableListOf<String>()
        lines += if (enabled) getString(R.string.status_blocking_on) else getString(R.string.status_blocking_off)
        lines += if (roleHeld) {
            getString(R.string.status_screening_ok)
        } else {
            getString(R.string.status_screening_missing)
        }
        lines += if (contactsOk) {
            getString(R.string.status_contacts_ok)
        } else {
            getString(R.string.status_contacts_missing)
        }
        lines += if (smsOk) {
            getString(R.string.status_sms_ok)
        } else {
            getString(R.string.status_sms_missing)
        }
        lines += if (areaCodes.isEmpty()) {
            getString(R.string.status_area_codes_none)
        } else {
            getString(R.string.status_area_codes, areaCodes.joinToString(", "))
        }
        lines += if (suppressVm) {
            getString(R.string.status_suppress_vm_on)
        } else {
            getString(R.string.status_suppress_vm_off)
        }
        if (suppressVm) {
            lines += if (BlockerSettings.isVmSuppressArmed(this)) {
                getString(R.string.status_vm_mute_armed)
            } else {
                getString(R.string.status_vm_mute_idle)
            }
        }
        lines += if (notifAccess) {
            getString(R.string.status_notif_access_ok)
        } else {
            getString(R.string.status_notif_access_missing)
        }
        lines += getString(R.string.status_sms_note)

        statusText.text = lines.joinToString("\n")
    }

    private fun refreshProbeStatus() {
        probeStatusText.text = NotificationProbe.statusSummary(this)
    }

    private fun refreshLog() {
        val entries = BlockLog.load(this)
        logHeader.text = getString(R.string.blocked_history_header, entries.size)
        blockedListContainer.removeAllViews()

        if (entries.isEmpty()) {
            emptyLogText.visibility = View.VISIBLE
            blockedListContainer.visibility = View.GONE
            return
        }

        emptyLogText.visibility = View.GONE
        blockedListContainer.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        for (entry in entries) {
            val row = inflater.inflate(R.layout.item_blocked_number, blockedListContainer, false)
            row.findViewById<TextView>(R.id.itemNumber).text = entry.number
            row.findViewById<TextView>(R.id.itemMeta).text = buildString {
                append(if (entry.type == "sms") getString(R.string.type_sms) else getString(R.string.type_call))
                append(" · ")
                append(dateFormat.format(Date(entry.timestampMs)))
            }
            blockedListContainer.addView(row)
        }
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return

        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        requestCallScreeningLauncher.launch(intent)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            needed += Manifest.permission.READ_CONTACTS
        }
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            needed += Manifest.permission.RECEIVE_SMS
        }
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            needed += Manifest.permission.READ_PHONE_STATE
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    private fun isCallScreeningRoleHeld(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
            ?: imm.hideSoftInputFromWindow(areaCodeInput.windowToken, 0)
    }
}
