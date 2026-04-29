package com.warzone.changer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.warzone.changer.R
import com.warzone.changer.data.LocationStore
import com.warzone.changer.model.SelectedLocation

class LocationPickerActivity : AppCompatActivity() {

    data class Region(
        val adcode: Int = 0,
        val shortName: String = "",
        val fullName: String = "",
        val list: List<Region>? = null
    )

    private lateinit var lvProvince: ListView
    private lateinit var lvCity: ListView
    private lateinit var lvDistrict: ListView
    private lateinit var tvBreadcrumb: TextView
    private lateinit var btnConfirm: Button
    private lateinit var tvTitle: TextView
    private lateinit var etSearch: EditText

    private var allProvinces: List<Region> = emptyList()
    private var displayList: List<Region> = emptyList()

    private var selectedProvince: Region? = null
    private var selectedCity: Region? = null
    private var selectedDistrict: Region? = null
    private var currentLevel = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        lvProvince = findViewById(R.id.lv_province)
        lvCity = findViewById(R.id.lv_city)
        lvDistrict = findViewById(R.id.lv_district)
        tvBreadcrumb = findViewById(R.id.tv_breadcrumb)
        btnConfirm = findViewById(R.id.btn_confirm)
        tvTitle = findViewById(R.id.tv_title)
        etSearch = findViewById(R.id.et_search)

        loadRegions()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterByKeyword(s?.toString()?.trim() ?: "") }
        })

        // Province click
        lvProvince.setOnItemClickListener { _, _, pos, _ ->
            if (pos < displayList.size) {
                selectedProvince = displayList[pos]
                selectedCity = null; selectedDistrict = null
                etSearch.text.clear()
                val children = selectedProvince?.list ?: emptyList()
                if (children.isEmpty()) {
                    // No city level -> confirm at province
                    updateBreadcrumb(); btnConfirm.isEnabled = true
                } else {
                    showList(1, children)
                }
            }
        }

        // City click
        lvCity.setOnItemClickListener { _, _, pos, _ ->
            if (pos < displayList.size) {
                selectedCity = displayList[pos]
                selectedDistrict = null
                etSearch.text.clear()
                val children = selectedCity?.list ?: emptyList()
                if (children.isEmpty()) {
                    // No district -> confirm at city
                    updateBreadcrumb(); btnConfirm.isEnabled = true
                } else {
                    showList(2, children)
                }
            }
        }

        // District click
        lvDistrict.setOnItemClickListener { _, _, pos, _ ->
            if (pos < displayList.size) {
                selectedDistrict = displayList[pos]
                updateBreadcrumb(); btnConfirm.isEnabled = true
            }
        }

        btnConfirm.setOnClickListener { saveLocation() }
    }

    private fun loadRegions() {
        try {
            val json = assets.open("warzone.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Region>>() {}.type
            allProvinces = Gson().fromJson(json, type)
            showList(0, allProvinces)
        } catch (e: Exception) {
            Toast.makeText(this, "加载失败: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showList(level: Int, items: List<Region>) {
        currentLevel = level
        displayList = items
        btnConfirm.isEnabled = false
        lvProvince.visibility = if (level == 0) View.VISIBLE else View.GONE
        lvCity.visibility = if (level == 1) View.VISIBLE else View.GONE
        lvDistrict.visibility = if (level == 2) View.VISIBLE else View.GONE
        tvTitle.text = when (level) { 0 -> "选择省份"; 1 -> "选择城市"; else -> "选择区县" }
        etSearch.hint = "搜索"
        refreshAdapter()
        updateBreadcrumb()
    }

    private fun filterByKeyword(keyword: String) {
        val source = when (currentLevel) {
            0 -> allProvinces
            1 -> selectedProvince?.list ?: emptyList()
            2 -> selectedCity?.list ?: emptyList()
            else -> emptyList()
        }
        displayList = if (keyword.isEmpty()) source else source.filter {
            it.fullName.contains(keyword, true) || it.shortName.contains(keyword, true)
        }
        refreshAdapter()
    }

    private fun refreshAdapter() {
        val lv = when (currentLevel) { 0 -> lvProvince; 1 -> lvCity; else -> lvDistrict }
        lv.adapter = makeAdapter()
    }

    private fun updateBreadcrumb() {
        val parts = mutableListOf<String>()
        selectedProvince?.let { parts.add(it.shortName) }
        selectedCity?.let { parts.add(it.shortName) }
        selectedDistrict?.let { parts.add(it.shortName) }
        tvBreadcrumb.text = parts.joinToString(" > ").ifEmpty { "请选择" }
    }

    private fun makeAdapter(): ArrayAdapter<Region> {
        return object : ArrayAdapter<Region>(this, android.R.layout.simple_list_item_1, displayList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as TextView).apply {
                    text = displayList[position].fullName
                    setTextColor(0xFFE2E8F0.toInt()); textSize = 15f; setPadding(36, 24, 36, 24)
                }
                return v
            }
        }
    }

    private fun saveLocation() {
        val province = selectedProvince ?: return
        val city = selectedCity ?: province
        val district = selectedDistrict
        val adcode = district?.adcode ?: city.adcode ?: province.adcode
        LocationStore.save(this, SelectedLocation(
            province = province.fullName, city = city.fullName,
            district = district?.fullName ?: "", latitude = 0.0, longitude = 0.0,
            adcode = adcode.toString()
        ))
        Toast.makeText(this, "已设置", Toast.LENGTH_SHORT).show()
        finish()
    }
}
