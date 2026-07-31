package com.example.unknownblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var toggle: Switch
    private lateinit var statusText: TextView
    private lateinit var logHeader: TextView
    private lateinit var emptyLogText: TextView
    private lateinit var blockedList: ListView
    private lateinit var clearLogButton: Button
    private lateinit var refreshButton: Button

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
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
                // Keep toggle on — user can grant later from system settings
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.toggleSwitch)
        statusText = findViewById(R.id.statusText)
        logHeader = findViewById(R.id.logHeader)
        emptyLogText = findViewById(R.id.emptyLogText)
        blockedList = findViewById(R.id.blockedList)
        clearLogButton = findViewById(R.id.clearLogButton)
        refreshButton = findViewById(R.id.refreshButton)

        toggle.isChecked = prefs.getBoolean(KEY_ENABLED, false)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
            if (isChecked) {
                requestPermissionsIfNeeded()
                requestCallScreeningRole()
            }
            refreshStatus()
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
            Toast.makeText(this, R.string.refreshed, Toast.LENGTH_SHORT).show()
        }

        if (toggle.isChecked) {
            requestPermissionsIfNeeded()
            requestCallScreeningRole()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLog()
    }

    private fun refreshStatus() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val roleHeld = isCallScreeningRoleHeld()
        val contactsOk = hasPermission(Manifest.permission.READ_CONTACTS)
        val smsOk = hasPermission(Manifest.permission.RECEIVE_SMS)

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
        lines += getString(R.string.status_sms_note)

        statusText.text = lines.joinToString("\n")
    }

    private fun refreshLog() {
        val entries = BlockLog.load(this)
        logHeader.text = getString(R.string.blocked_history_header, entries.size)

        if (entries.isEmpty()) {
            emptyLogText.visibility = View.VISIBLE
            blockedList.visibility = View.GONE
            blockedList.adapter = null
        } else {
            emptyLogText.visibility = View.GONE
            blockedList.visibility = View.VISIBLE
            blockedList.adapter = BlockedAdapter(entries)
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

    private inner class BlockedAdapter(
        private val items: List<BlockLog.Entry>
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): BlockLog.Entry = items[position]
        override fun getItemId(position: Int): Long = items[position].timestampMs
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_number, parent, false)
            val entry = items[position]
            view.findViewById<TextView>(R.id.itemNumber).text = entry.number
            view.findViewById<TextView>(R.id.itemMeta).text = buildString {
                append(if (entry.type == "sms") getString(R.string.type_sms) else getString(R.string.type_call))
                append(" · ")
                append(dateFormat.format(Date(entry.timestampMs)))
            }
            return view
        }
    }

    companion object {
        private const val PREFS_NAME = "blocker_prefs"
        private const val KEY_ENABLED = "enabled"
    }
}
