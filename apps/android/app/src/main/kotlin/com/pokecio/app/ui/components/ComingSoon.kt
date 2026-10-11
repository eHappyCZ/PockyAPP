package com.pokecio.app.ui.components

import android.content.Context
import android.widget.Toast
import com.pokecio.app.R

fun comingSoon(context: Context) {
    Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
}
