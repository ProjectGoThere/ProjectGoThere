package com.example.projectgothere

import android.R
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import org.json.JSONArray
import org.json.JSONException
import java.util.*


/**
 * AutoCompleteTextView taking its list of values from a shared preference.
 *
 * Use the static method storePreference(value) to add an entry in these preferences
 * (typically when the user "uses" a value he just entered in this edittext view).
 *
 * @author M.Kergall
 */
class AutoCompleteOnPreferences : AppCompatAutoCompleteTextView {
    constructor(context: Context?) : super(context!!)
    constructor(arg0: Context?, arg1: AttributeSet?) : super(arg0!!, arg1)
    constructor(arg0: Context?, arg1: AttributeSet?, arg2: Int) : super(
        arg0!!, arg1, arg2
    )

    override fun enoughToFilter(): Boolean {
        return true
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            setPreferences()
            if (adapter != null) {
                performFiltering(text, 0)
            }
        }
    }

    private lateinit var mAppKey: String
    private lateinit var mKey: String

    /**
     * Specify which application key and which preference name will be used.
     * @param appKey Shared Preferences application key
     * @param prefName Preference name
     */
    fun setPrefKeys(appKey: String, prefName: String) {
        mAppKey = appKey
        mKey = prefName
    }

    private fun setPreferences() {
        val prefs = preferences
        val adapter = ArrayAdapter(
            context,
            R.layout.simple_dropdown_item_1line, prefs
        )
        setAdapter<ArrayAdapter<String>>(adapter)
    }

    private val preferences: ArrayList<String>

        get() {
            val prefs = context.getSharedPreferences(mAppKey, Context.MODE_PRIVATE)
            val prefString = prefs.getString(mKey, "[]")!!
            return try {
                val prefArray = JSONArray(prefString)
                val result = arrayListOf<String>()
                for (i in 0 until prefArray.length()) {
                    result.add(prefArray.getString(i))
                }
                result
            } catch (e: JSONException) {
                e.printStackTrace()
                arrayListOf<String>()
            }
        }

    companion object {
        /**
         * Add a value in a list of preferences referenced by an appKey and a prefName.
         * @param context
         * @param value to add in the list.
         * @param appKey application key
         * @param prefName name of the preferences list
         */
        fun storePreference(context: Context, value: String, appKey: String?, prefName: String?) {
            val prefs = context.getSharedPreferences(appKey, Context.MODE_PRIVATE)
            var prefValues = prefs.getString(prefName, "[]")
            var prefValuesArray: JSONArray
            try {
                prefValuesArray = JSONArray(prefValues)
                val prefValuesList = LinkedList<String>()
                for (i in 0 until prefValuesArray.length()) {
                    val prefValue = prefValuesArray.getString(i)
                    if (prefValue != value) prefValuesList.addLast(prefValue)
                    //else, don't add it => it will be added at the beginning, as a new one...
                }
                //add the new one at the beginning:
                prefValuesList.addFirst(value)
                //remove last entry if too much:
                if (prefValuesList.size > 20) prefValuesList.removeLast()

                //Rebuild JSON string:
                prefValuesArray = JSONArray()
                for (s in prefValuesList) {
                    prefValuesArray.put(s)
                }
                prefValues = prefValuesArray.toString()
                val ed = prefs.edit()
                ed.putString(prefName, prefValues)
                ed.apply()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}