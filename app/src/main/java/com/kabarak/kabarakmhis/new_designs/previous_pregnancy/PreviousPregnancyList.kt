package com.kabarak.kabarakmhis.new_designs.previous_pregnancy

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.kabarak.kabarakmhis.R
import com.kabarak.kabarakmhis.fhir.FhirApplication
import com.kabarak.kabarakmhis.fhir.viewmodels.PatientDetailsViewModel
import com.kabarak.kabarakmhis.helperclass.DbObservationValues
import com.kabarak.kabarakmhis.helperclass.FormatterClass
import com.kabarak.kabarakmhis.network_request.requests.RetrofitCallsFhir
import com.kabarak.kabarakmhis.new_designs.adapter.FhirEncounterAdapter
import com.kabarak.kabarakmhis.new_designs.data_class.DbFhirEncounter
import com.kabarak.kabarakmhis.new_designs.data_class.DbResourceViews
import com.kabarak.kabarakmhis.new_designs.roomdb.KabarakViewModel
import com.kabarak.kabarakmhis.new_designs.screens.PatientProfile
import kotlinx.android.synthetic.main.activity_previous_pregnancy_list.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreviousPregnancyList : AppCompatActivity() {

    private val retrofitCallsFhir = RetrofitCallsFhir()
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private val formatter = FormatterClass()
    private lateinit var kabarakViewModel: KabarakViewModel

    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_previous_pregnancy_list)

        title = "Previous Pregnancy List"

        btnVisit.setOnClickListener {
            startActivity(Intent(this, PreviousPregnancy::class.java))
        }

        patientId = formatterClass.retrieveSharedPreference(this, "patientId").toString()
        fhirEngine = FhirApplication.fhirEngine(this)

        patientDetailsViewModel = ViewModelProvider(this,
            PatientDetailsViewModel.PatientDetailsViewModelFactory(application,fhirEngine, patientId)
        )[PatientDetailsViewModel::class.java]

        kabarakViewModel = KabarakViewModel(this.applicationContext as Application)


        layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
        
    }

    override fun onStart() {
        super.onStart()

        CoroutineScope(Dispatchers.IO).launch {

            formatter.deleteSharedPreference(this@PreviousPregnancyList, DbResourceViews.PREVIOUS_PREGNANCY.name)


            val observationList = patientDetailsViewModel.getObservationFromEncounter(DbResourceViews.PREVIOUS_PREGNANCY.name)

            CoroutineScope(Dispatchers.Main).launch {

                if (observationList.isNotEmpty()){
                    no_record.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }else{
                    no_record.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }

                val encounterList = ArrayList<DbFhirEncounter>()
                observationList.forEachIndexed { index, encounterItem ->

//                    val pos = index + 1
//                    val visitNo = formatter.getNumber(pos)

                    val encounterId = encounterItem.id
                    //Get observation data from the encounter
                    val observationEncounterList = patientDetailsViewModel.getObservationsPerCodeFromEncounter(
                        formatter.getCodes(DbObservationValues.PREGNANCY_ORDER.name), encounterId)

                    observationEncounterList.forEach {

                        val value = it.value
                        val encounterName = "$value Pregnancy"
                        val encounterType = encounterItem.code

                        val dbFhirEncounter = DbFhirEncounter(
                            id = encounterId,
                            encounterName = encounterName,
                            encounterType = encounterType
                        )
                        encounterList.add(dbFhirEncounter)

                    }

                }

                val configurationListingAdapter = FhirEncounterAdapter(
                    encounterList,this@PreviousPregnancyList, DbResourceViews.PREVIOUS_PREGNANCY.name)
                recyclerView.adapter = configurationListingAdapter
            }





        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.profile_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.profile -> {

                startActivity(Intent(this, PatientProfile::class.java))
                finish()

                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}