package com.warzone.changer.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.warzone.changer.R
import com.warzone.changer.data.LocationStore
import org.json.JSONArray
import java.io.InputStream

/**
 * 战区选择界面
 * 加载战区.json数据，展示省→市→区三级联动选择
 */
class LocationPickerActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var lvProvinces: ListView
    private lateinit var lvCities: ListView
    private lateinit var lvDistricts: ListView

    private var regions: JSONArray? = null
    private var currentProvinces: List<Pair<String, String>> = emptyList() // adcode, name
    private var currentCities: List<Pair<String, String>> = emptyList()
    private var currentDistricts: List<Pair<String, String>> = emptyList()

    private var selectedProvince = ""
    private var selectedCity = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        etSearch = findViewById(R.id.et_search)
        lvProvinces = findViewById(R.id.lv_provinces)
        lvCities = findViewById(R.id.lv_cities)
        lvDistricts = findViewById(R.id.lv_districts)

        loadRegions()
        showProvinces()

        lvProvinces.setOnItemClickListener { _, _, position, _ ->
            if (position < currentProvinces.size) {
                selectedProvince = currentProvinces[position].second
                showCities(currentProvinces[position].first)
            }
        }

        lvCities.setOnItemClickListener { _, _, position, _ ->
            if (position < currentCities.size) {
                selectedCity = currentCities[position].second
                showDistricts(currentCities[position].first)
            }
        }

        lvDistricts.setOnItemClickListener { _, _, position, _ ->
            if (position < currentDistricts.size) {
                val district = currentDistricts[position]
                LocationStore.saveLocation(
                    this,
                    district.first,  // adcode
                    district.second, // name
                    selectedProvince,
                    selectedCity
                )
                setResult(Activity.RESULT_OK)
                Toast.makeText(this, "已选择: $selectedProvince $selectedCity ${district.second}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterProvinces(s?.toString() ?: "")
            }
        })
    }

    private fun loadRegions() {
        try {
            val inputStream: InputStream = assets.open("warzone.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            regions = JSONArray(json)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载战区数据失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProvinces() {
        val list = mutableListOf<String>()
        currentProvinces = mutableListOf()

        regions?.let { arr ->
            for (i in 0 until arr.length()) {
                val province = arr.getJSONObject(i)
                val adcode = province.getString("adcode")
                val name = province.getString("shortName")
                (currentProvinces as MutableList).add(Pair(adcode, name))
                list.add(name)
            }
        }

        lvProvinces.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        lvCities.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emptyList<String>())
        lvDistricts.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emptyList<String>())
    }

    private fun showCities(provinceAdcode: String) {
        val list = mutableListOf<String>()
        currentCities = mutableListOf()

        regions?.let { arr ->
            for (i in 0 until arr.length()) {
                val province = arr.getJSONObject(i)
                if (province.getString("adcode") == provinceAdcode) {
                    val cities = province.getJSONArray("list")
                    for (j in 0 until cities.length()) {
                        val city = cities.getJSONObject(j)
                        val adcode = city.getString("adcode")
                        val name = city.getString("shortName")
                        (currentCities as MutableList).add(Pair(adcode, name))
                        list.add(name)
                    }
                    break
                }
            }
        }

        lvCities.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        lvDistricts.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emptyList<String>())
    }

    private fun showDistricts(cityAdcode: String) {
        val list = mutableListOf<String>()
        currentDistricts = mutableListOf()

        regions?.let { arr ->
            for (i in 0 until arr.length()) {
                val province = arr.getJSONObject(i)
                val cities = province.getJSONArray("list")
                for (j in 0 until cities.length()) {
                    val city = cities.getJSONObject(j)
                    if (city.getString("adcode") == cityAdcode) {
                        val districts = city.getJSONArray("list")
                        for (k in 0 until districts.length()) {
                            val district = districts.getJSONObject(k)
                            val adcode = district.getString("adcode")
                            val name = district.getString("shortName")
                            (currentDistricts as MutableList).add(Pair(adcode, name))
                            list.add(name)
                        }
                        break
                    }
                }
            }
        }

        // 如果没有子区域，直接用市级adcode
        if (currentDistricts.isEmpty()) {
            (currentDistricts as MutableList).add(Pair(cityAdcode, selectedCity))
            list.add("（点击直接选择市级战区）")
        }

        lvDistricts.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
    }

    private fun filterProvinces(query: String) {
        if (query.isEmpty()) {
            showProvinces()
            return
        }

        val filtered = currentProvinces.filter { it.second.contains(query) }
        val list = filtered.map { it.second }

        currentProvinces = filtered
        lvProvinces.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
    }
}