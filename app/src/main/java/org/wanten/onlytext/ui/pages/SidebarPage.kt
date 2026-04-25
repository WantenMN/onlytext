package org.wanten.onlytext.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

@Composable
fun SidebarPage(
    modifier: Modifier = Modifier,
    title: String = "OnlyText"
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        ModalDrawerSheet(
            modifier = Modifier.fillMaxSize(),
            drawerShape = RectangleShape,
        ) {
            Text(title, modifier = Modifier.padding(16.dp))
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text("All Notes") },
                selected = true,
                onClick = { /* TODO */ },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = false,
                onClick = { /* TODO */ },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}
