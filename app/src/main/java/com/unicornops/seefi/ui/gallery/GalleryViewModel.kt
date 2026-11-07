package com.unicornops.seefi.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unicornops.seefi.AppContainer
import com.unicornops.seefi.SeefiApplication
import com.unicornops.seefi.data.ReceivedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(private val container: AppContainer) : ViewModel() {

    private val _photos = MutableStateFlow<List<ReceivedPhoto>>(emptyList())
    val photos: StateFlow<List<ReceivedPhoto>> = _photos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _photos.value = container.photoStore.queryPhotos()
            _loading.value = false
        }
    }

    fun delete(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (container.photoStore.deletePhoto(uri)) {
                _photos.value = _photos.value.filterNot { it.uri == uri }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SeefiApplication
                GalleryViewModel(app.container)
            }
        }
    }
}
