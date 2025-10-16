package com.example.meydantestapp.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.meydantestapp.R

/**
 * Centralized access to shared report section headings so the UI and PDF stay in sync.
 */
object ReportHeadings {

    @StringRes
    private val activitiesRes: Int = R.string.report_section_activities

    @StringRes
    private val equipmentRes: Int = R.string.report_section_equipment

    @StringRes
    private val obstaclesRes: Int = R.string.report_section_obstacles

    @StringRes
    private val projectLocationRes: Int = R.string.report_section_project_location

    @StringRes
    private val infoRes: Int = R.string.report_section_info

    @StringRes
    private val workforceRes: Int = R.string.report_section_workforce

    @StringRes
    private val notesRes: Int = R.string.report_section_notes

    @StringRes
    private val photosRes: Int = R.string.report_section_photos

    @StringRes
    private val sitePagesRes: Int = R.string.report_section_site_pages

    fun activities(context: Context): String = context.getString(activitiesRes)

    fun equipment(context: Context): String = context.getString(equipmentRes)

    fun obstacles(context: Context): String = context.getString(obstaclesRes)

    fun projectLocation(context: Context): String = context.getString(projectLocationRes)

    fun info(context: Context): String = context.getString(infoRes)

    fun workforce(context: Context): String = context.getString(workforceRes)

    fun notes(context: Context): String = context.getString(notesRes)

    fun photos(context: Context): String = context.getString(photosRes)

    fun sitePages(context: Context): String = context.getString(sitePagesRes)
}
