package br.edu.ifsp.scl.sdm.covid19infosdm

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import br.edu.ifsp.scl.sdm.covid19infosdm.model.dataclass.ByCountryResponseList
import br.edu.ifsp.scl.sdm.covid19infosdm.model.dataclass.ByCountryResponseListItem
import br.edu.ifsp.scl.sdm.covid19infosdm.model.dataclass.DayOneResponseList
import br.edu.ifsp.scl.sdm.covid19infosdm.model.dataclass.DayOneResponseListItem
import br.edu.ifsp.scl.sdm.covid19infosdm.viewmodel.Covid19ViewModel
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: Covid19ViewModel
    private lateinit var countryAdapter: ArrayAdapter<String>
    private lateinit var countryNameSlugMap: MutableMap<String, String>

    /* Classe para os serviços que serão acessados */
    private enum class Information(val type: String){
        DAY_ONE("Dia 1"),
        BY_COUNTRY("Por país")
    }

    /* Classe para o status que será buscado no serviço */
    private enum class Status(val type: String){
        CONFIRMED("Confirmados"),
        RECOVERED("Curados"),
        DEATHS("Mortes")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = Covid19ViewModel(this)

        countryAdapterInit()

        informationAdapterInit()

        statusAdapterInit()
    }

    fun onRetrieveClick(view: View) {
        if(countrySp.selectedItem != null){
            when (infoSp.selectedItem.toString()) {
                Information.DAY_ONE.type -> { fetchDayOne() }
                Information.BY_COUNTRY.type -> { fetchByCountry() }
            }
        }else{
            Toast.makeText(this, "Selecione um país", Toast.LENGTH_LONG).show()
        }

    }

    private fun countryAdapterInit() {
        /* Preenchido por Web Service */
        countryAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        countryNameSlugMap = mutableMapOf()
        countrySp.adapter = countryAdapter
        viewModel.fetchCountries().observe(
            this,
            Observer { countryList ->
                countryList.sortedBy { it.country }.forEach { countryListItem ->
                    if ( countryListItem.country.isNotEmpty()) {
                        countryAdapter.add(countryListItem.country)
                        countryNameSlugMap[countryListItem.country] = countryListItem.slug
                    }
                }
            }
        )
    }

    private fun informationAdapterInit() {
        val informationList = arrayListOf<String>()
        Information.values().forEach { informationList.add(it.type) }

        infoSp.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, informationList)
        infoSp.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }

            // A nova versão dos serviços alterou a forma como dispomos os dados
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    Information.DAY_ONE.ordinal -> {
                        viewModeTv.visibility = View.VISIBLE
                        viewModeRg.visibility = View.VISIBLE
                    }
                    Information.BY_COUNTRY.ordinal -> {
                        viewModeTv.visibility = View.GONE
                        viewModeRg.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun statusAdapterInit() {
        val statusList = arrayListOf<String>()
        Status.values().forEach { statusList.add(it.type) }

        statusSp.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, statusList)
    }

    private fun fetchDayOne() {
        val countrySlug = countryNameSlugMap[countrySp.selectedItem.toString()]!!
        getDayOne(countrySlug)
    }

    private fun getDayOne(countrySlug: String) {
        viewModel.fetchDayOne(countrySlug, statusSp.selectedItem.toString()).observe(
            this,
            Observer { casesList ->

                    try {

                        if (viewModeTextRb.isChecked) {
                            /* Modo texto */
                            modoGrafico(ligado = false)
                            resultTv.text = casesListToString(casesList)
                        } else {
                            /* Modo gráfico */
                            modoGrafico(ligado = true)
                            resultGv.removeAllSeries()
                            resultGv.gridLabelRenderer.resetStyles()

                            /* Preparando pontos */
                            val pointsArrayList = arrayListOf<DataPoint>()
                            casesList.forEach {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(
                                    it.date.substring(
                                        0,
                                        10
                                    )
                                )
                                val point = DataPoint(date, it.cases.toDouble())
                                pointsArrayList.add(point)
                            }
                            val pointsSeries = LineGraphSeries(pointsArrayList.toTypedArray())
                            resultGv.addSeries(pointsSeries)

                            /* Formatando gráfico */
                            resultGv.gridLabelRenderer.setHumanRounding(false)
                            resultGv.gridLabelRenderer.labelFormatter =
                                DateAsXAxisLabelFormatter(this)

                            resultGv.gridLabelRenderer.numHorizontalLabels = 4
                            val primeiraData = Date(pointsArrayList.first().x.toLong())
                            val ultimaData = Date(pointsArrayList.last().x.toLong())
                            resultGv.viewport.setMinX(primeiraData.time.toDouble())
                            resultGv.viewport.setMaxX(ultimaData.time.toDouble())
                            resultGv.viewport.isXAxisBoundsManual = true

                            resultGv.gridLabelRenderer.numVerticalLabels = 4
                            resultGv.viewport.setMinY(pointsArrayList.first().y)
                            resultGv.viewport.setMaxY(pointsArrayList.last().y)
                            resultGv.viewport.isYAxisBoundsManual = true
                        }
                    }catch (e : Exception){
                        Toast.makeText(this,
                            "Houve um erro, tente novamente", Toast.LENGTH_LONG).show()
                    }
            }
        )
    }

    private fun fetchByCountry() {
        val countrySlug = countryNameSlugMap[countrySp.selectedItem.toString()]!!

        modoGrafico(ligado = false)
        viewModel.fetchByCountry(countrySlug, statusSp.selectedItem.toString()).observe(
            this,
            Observer { casesList ->
                try {
                    resultTv.text = casesListToString(casesList)
                }catch (e : Exception){
                    Toast.makeText(this,
                        "Houve um erro, tente novamente", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun modoGrafico(ligado: Boolean) {
        if (ligado) {
            resultTv.visibility = View.GONE
            resultGv.visibility = View.VISIBLE
        }
        else {
            resultTv.visibility = View.VISIBLE
            resultGv.visibility = View.GONE
        }
    }

    private inline fun <reified  T: ArrayList<*>> casesListToString(responseList: T): String {
        val resultSb = StringBuffer()



        // Usando class.java para não ter que adicionar biblioteca de reflexão Kotlin
        responseList.forEach {
            when(T::class.java) {
                DayOneResponseList::class.java -> {
                    with (it as DayOneResponseListItem) {
                        resultSb.append("Casos: ${this.cases}\n")
                        resultSb.append("Data: ${formatDate(this.date)}\n\n")
                    }
                }
                ByCountryResponseList::class.java -> {
                    with (it as ByCountryResponseListItem) {
                        this.province.takeIf { !this.province.isNullOrEmpty() }?.let { province ->
                            resultSb.append("Estado/Província: ${province}\n")
                        }
                        this.city.takeIf { !this.city.isNullOrEmpty() }?.let { city ->
                            resultSb.append("Cidade: ${city}\n")
                        }

                        resultSb.append("Casos: ${this.cases}\n")

                        resultSb.append("Data: ${formatDate(this.date)}\n\n")
                    }
                }
            }
        }

        return resultSb.toString()
    }

    private fun formatDate(date : String) : String{
        val formatter : SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd");
        val date : Date = formatter.parse(date);
        formatter.applyPattern("dd/MM/yyyy");
        return formatter.format(date);
    }
}
