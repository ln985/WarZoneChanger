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
import java.io.File

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
    private var provinces: List<Region> = emptyList()
    private var cities: List<Region> = emptyList()
    private var districts: List<Region> = emptyList()

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
            override fun afterTextChanged(s: Editable?) {
                filterByKeyword(s?.toString()?.trim() ?: "")
            }
        })

        lvProvince.setOnItemClickListener { _, _, pos, _ ->
            if (pos < provinces.size) {
                selectedProvince = provinces[pos]
                selectedCity = null; selectedDistrict = null
                cities = selectedProvince?.list ?: emptyList()
                districts = emptyList()
                etSearch.text.clear()
                if (cities.isEmpty() || isLeafLevel(cities)) {
                    selectedCity = null
                    updateBreadcrumb()
                    btnConfirm.isEnabled = true
                } else {
                    showCityList()
                }
            }
        }

        lvCity.setOnItemClickListener { _, _, pos, _ ->
            if (pos < cities.size) {
                selectedCity = cities[pos]
                selectedDistrict = null
                districts = selectedCity?.list ?: emptyList()
                etSearch.text.clear()
                if (districts.isEmpty() || isLeafLevel(districts)) {
                    selectedDistrict = null
                    updateBreadcrumb()
                    btnConfirm.isEnabled = true
                } else {
                    showDistrictList()
                }
            }
        }

        lvDistrict.setOnItemClickListener { _, _, pos, _ ->
            if (pos < districts.size) {
                selectedDistrict = districts[pos]
                updateBreadcrumb()
                btnConfirm.isEnabled = true
            }
        }

        btnConfirm.setOnClickListener { saveLocation() }
    }

    private fun isLeafLevel(items: List<Region>): Boolean {
        return items.all { it.list.isNullOrEmpty() }
    }

    private fun loadRegions() {
        try {
            val json = try {
                assets.open("warzone.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                val file = File("/storage/emulated/0/\u6218\u533a.json")
                if (file.exists()) file.readText() else throw e
            }
            val type = object : TypeToken<List<Region>>() {}.type
            allProvinces = Gson().fromJson(json, type)
            provinces = allProvinces
            showProvinceList()
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun filterByKeyword(keyword: String) {
        if (keyword.isEmpty()) {
            when (currentLevel) {
                0 -> provinces = allProvinces
                1 -> cities = selectedProvince?.list ?: emptyList()
                2 -> districts = selectedCity?.list ?: emptyList()
            }
        } else {
            when (currentLevel) {
                0 -> provinces = allProvinces.filter {
                    it.fullName.contains(keyword, true) || it.shortName.contains(keyword, true)
                }
                1 -> cities = (selectedProvince?.list ?: emptyList()).filter {
                    it.fullName.contains(keyword, true) || it.shortName.contains(keyword, true)
                }
                2 -> districts = (selectedCity?.list ?: emptyList()).filter {
                    it.fullName.contains(keyword, true) || it.shortName.contains(keyword, true)
                }
            }
        }
        refreshCurrentList()
    }

    private fun refreshCurrentList() {
        when (currentLevel) {
            0 -> lvProvince.adapter = makeAdapter(provinces)
            1 -> lvCity.adapter = makeAdapter(cities)
            2 -> lvDistrict.adapter = makeAdapter(districts)
        }
    }

    private fun showProvinceList() {
        currentLevel = 0
        lvProvince.visibility = View.VISIBLE
        lvCity.visibility = View.GONE
        lvDistrict.visibility = View.GONE
        btnConfirm.isEnabled = false
        tvTitle.text = "\u9009\u62e9\u7701\u4efd"
        etSearch.hint = "\ud83d\udd0d Search..."
        provinces = allProvinces
        lvProvince.adapter = makeAdapter(provinces)
        updateBreadcrumb()
    }

    private fun showCityList() {
        currentLevel = 1
        lvProvince.visibility = View.GONE
        lvCity.visibility = View.VISIBLE
        lvDistrict.visibility = View.GONE
        btnConfirm.isEnabled = false
        tvTitle.text = "\u9009\u62e9\u57ce\u5e02"
        etSearch.hint = "\ud83d\udd0d Search..."
        cities = selectedProvince?.list ?: emptyList()
        lvCity.adapter = makeAdapter(cities)
        updateBreadcrumb()
    }

    private fun showDistrictList() {
        currentLevel = 2
        lvProvince.visibility = View.GONE
        lvCity.visibility = View.GONE
        lvDistrict.visibility = View.VISIBLE
        btnConfirm.isEnabled = false
        tvTitle.text = "\u9009\u62e9\u533a\u53bf"
        etSearch.hint = "\ud83d\udd0d Search..."
        districts = selectedCity?.list ?: emptyList()
        lvDistrict.adapter = makeAdapter(districts)
        updateBreadcrumb()
    }

    private fun updateBreadcrumb() {
        val parts = mutableListOf<String>()
        selectedProvince?.let { parts.add(it.shortName) }
        selectedCity?.let { parts.add(it.shortName) }
        selectedDistrict?.let { parts.add(it.shortName) }
        tvBreadcrumb.text = parts.joinToString(" \u25b8 ").ifEmpty { "Please select" }
    }

    private fun makeAdapter(regions: List<Region>): ArrayAdapter<Region> {
        return object : ArrayAdapter<Region>(this, android.R.layout.simple_list_item_1, regions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val r = regions[position]
                (view as TextView).apply {
                    text = r.fullName
                    setTextColor(0xFFE2E8F0.toInt())
                    textSize = 15f
                    setPadding(36, 24, 36, 24)
                }
                return view
            }
        }
    }

    private fun saveLocation() {
        val province = selectedProvince ?: return
        val city = selectedCity ?: province
        val district = selectedDistrict

        val location = SelectedLocation(
            province = province.fullName,
            city = city.fullName,
            district = district?.fullName ?: "",
            latitude = 0.0,
            longitude = 0.0,
            adcode = (district?.adcode ?: city.adcode ?: province.adcode).toString()
        )
        LocationStore.save(this, location)
        Toast.makeText(this, "Set: ${location.getFormattedAddress()}", Toast.LENGTH_SHORT).show()
        finish()
    }
}
