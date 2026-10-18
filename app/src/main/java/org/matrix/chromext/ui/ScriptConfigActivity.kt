package org.matrix.chromext.ui

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toolbar
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.runBlocking
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.ScriptMetadata
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptParser

class ScriptConfigActivity : Activity(), OnApplyWindowInsetsListener {
  val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
  val sv by lazy { findViewById<ScrollView>(R.id.sv) }
  val sp_userRunAt by lazy { findViewById<Spinner>(R.id.sp_userRunAt) }
  val et_userMatch by lazy { findViewById<EditText>(R.id.et_userMatch) }
  val et_userInclude by lazy { findViewById<EditText>(R.id.et_userInclude) }
  val et_userExclude by lazy { findViewById<EditText>(R.id.et_userExclude) }
  val et_userConnect by lazy { findViewById<EditText>(R.id.et_userConnect) }

  var scriptId: String? = null
  var script: Script? = null
  lateinit var metadata: ScriptMetadata

  override fun onCreate(savedInstanceState: Bundle?) {
    Resource.enrich(this)

    setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_script_config)
    setActionBar(toolbar)

    scriptId = intent.getStringExtra("id")
    if (scriptId == null) {
      setTitle("not select script")
      return
    }
    runBlocking {
      script = ScriptDbManager.getScript(scriptId!!)
    }
    if (script == null) {
      setTitle("no script ($scriptId)")
      return
    }

    metadata = ScriptParser.parseMetadata(script?.meta!!)
    setTitle(metadata.name ?: scriptId)

    sp_userRunAt.adapter = ArrayAdapter(
      this,
      android.R.layout.simple_spinner_item,
      Script.RunAt.entries
    ).apply {
      setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }
    sp_userRunAt.setSelection(Script.RunAt.entries.indexOf(script!!.userRunAt))
    sp_userRunAt.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        script!!.userRunAt = Script.RunAt.entries[position]
      }

      override fun onNothingSelected(parent: AdapterView<*>) {}
    }
    et_userMatch.setText(script!!.userMatch)
    et_userMatch.doAfterTextChanged { editable ->
      script!!.userMatch = editable.toString()
    }
    et_userInclude.setText(script!!.userInclude)
    et_userInclude.doAfterTextChanged { editable ->
      script!!.userInclude = editable.toString()
    }
    et_userExclude.setText(script!!.userExclude)
    et_userExclude.doAfterTextChanged { editable ->
      script!!.userExclude = editable.toString()
    }
    et_userConnect.setText(script!!.userConnect)
    et_userConnect.doAfterTextChanged { editable ->
      script!!.userConnect = editable.toString()
    }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView, this)
  }

  override fun onApplyWindowInsets(
    v: View,
    insets: WindowInsetsCompat
  ): WindowInsetsCompat {
    insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { systemBars ->
      toolbar.updatePadding(top = systemBars.top)
      sv.updatePadding(bottom = systemBars.bottom)
    }
    return WindowInsetsCompat.CONSUMED
  }


  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_script_config, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.save -> {
        if (script != null) {
          runBlocking {
            ScriptDbManager.upsert(script!!)
          }
        } else {
          Log.toast(this, "no script")
        }
        return true
      }
    }
    return super.onMenuItemSelected(featureId, item)
  }
}