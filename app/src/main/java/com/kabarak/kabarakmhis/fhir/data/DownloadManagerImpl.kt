package com.kabarak.kabarakmhis.fhir.data

import com.google.android.fhir.sync.DownloadWorkManager

import com.google.android.fhir.SyncDownloadContext
import com.kabarak.kabarakmhis.fhir.data.Constants.DEMO_SERVER


import java.util.LinkedList
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.*

const val MAX_RESOURCE_COUNT = 40
const val SYNC_VALUE = "KENYA-KABARAK-MHIS6"
const val USER_ADDRESS = "NAIROBI"
const val USER_COUNTRY = "KE"
const val SYNC_PARAM = "address-country"

class DownloadManagerImpl : DownloadWorkManager {
    private val resourceTypeList = ResourceType.values().map { it.name }
    private val urls = LinkedList(listOf("Patient?$SYNC_PARAM=$SYNC_VALUE", "CarePlan", "ServiceRequest"))

    override suspend fun getNextRequestUrl(context: SyncDownloadContext): String? {
        var url = urls.poll() ?: return null

        val resourceTypeToDownload =
            ResourceType.fromCode(url.findAnyOf(resourceTypeList, ignoreCase = true)!!.second)
        context.getLatestTimestampFor(resourceTypeToDownload)?.let {
            url = affixLastUpdatedTimestamp(url!!, it)
        }

        return url
    }

    override suspend fun processResponse(response: Resource): Collection<Resource> {
        // As per FHIR documentation :
        // If the search fails (cannot be executed, not that there are no matches), the
        // return value SHALL be a status code 4xx or 5xx with an OperationOutcome.
        // See https://www.hl7.org/fhir/http.html#search for more details.
        if (response is OperationOutcome) {
            throw FHIRException(response.issueFirstRep.diagnostics)
        }
        // If the resource returned is a List containing Patients, extract Patient references and fetch
        // all resources related to the patient using the $everything operation.
        if (response is ListResource) {
            for (entry in response.entry) {
                val reference = Reference(entry.item.reference)
                if (reference.referenceElement.resourceType.equals("Patient")) {
                    val patientUrl = "${entry.item.reference}/\$everything"
                    urls.add(patientUrl)
                }
            }
        }


        // If the resource returned is a Bundle, check to see if there is a "next" relation referenced
        // in the Bundle.link component, if so, append the URL referenced to list of URLs to download.
        if (response is Bundle) {

         /*   for (i in 0 until response.total) {
                //if (response.entry[i].)
                val u = "${response.entry[i].fullUrl}/\$everything"
                urls.add(u)
            }*/

            for (entry in response.entry) {


                val type = entry.resource.resourceType.toString()

                if (type == "Patient") {
                    val patientUrl = "${entry.fullUrl}/\$everything"
                    urls.add(patientUrl)
                }

                if (type == "CarePlan") {
                    val no = entry.resource as CarePlan
                    val care = no.encounter.reference
                    val encounterUrl = "$DEMO_SERVER$care/\$everything"
                    urls.add(encounterUrl)

                }
                if (type == "ServiceRequest") {
                    val no = entry.resource as CarePlan
                    val care = no.encounter.reference
                    val encounterUrl = "$DEMO_SERVER$care/\$everything"
                    urls.add(encounterUrl)

                }
                if (type == "Encounter") {
                    val no = entry.resource as Encounter
                    if (no.hasPartOf()) {
                        val patientUrl = "${entry.fullUrl}/\$everything"
                        urls.add(patientUrl)
                    }

                }

            }


            val nextUrl = response.link.firstOrNull { component -> component.relation == "next" }?.url
            if (nextUrl != null) {
                urls.add(nextUrl)
            }
        }

        // Finally, extract the downloaded resources from the bundle.
        var bundleCollection: Collection<Resource> = mutableListOf()
        if (response is Bundle && response.type == Bundle.BundleType.SEARCHSET) {
            bundleCollection = response.entry.map { it.resource }
        }



        return bundleCollection
    }
}

/**
 * Affixes the last updated timestamp to the request URL.
 *
 * If the request URL includes the `$everything` parameter, the last updated timestamp will be
 * attached using the `_since` parameter. Otherwise, the last updated timestamp will be attached
 * using the `_lastUpdated` parameter.
 */
private fun affixLastUpdatedTimestamp(url: String, lastUpdated: String): String {
    var downloadUrl = url

    // Affix lastUpdate to a $everything query using _since as per:
    // https://hl7.org/fhir/operation-patient-everything.html
    if (downloadUrl.contains("\$everything")) {
        downloadUrl = "$downloadUrl?_since=$lastUpdated"
//        downloadUrl = "$downloadUrl"
    }

    // Affix lastUpdate to non-$everything queries as per:
    // https://hl7.org/fhir/operation-patient-everything.html
    if (!downloadUrl.contains("\$everything")) {

        downloadUrl = if (downloadUrl.contains("CarePlan")) {
            downloadUrl
        } else {
            "$downloadUrl&_lastUpdated=gt$lastUpdated"
        }


    }


    // Do not modify any URL set by a server that specifies the token of the page to return.
    if (downloadUrl.contains("&page_token")) {
        downloadUrl = url
    }

    return downloadUrl
}