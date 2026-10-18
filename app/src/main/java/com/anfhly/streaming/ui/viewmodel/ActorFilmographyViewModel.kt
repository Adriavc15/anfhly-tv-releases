package com.anfhly.streaming.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActorFilmographyViewModel : ViewModel() {

    private val repository = ContentRepository()

    private val _items = MutableStateFlow<List<Content>>(emptyList())
    val items: StateFlow<List<Content>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(actorName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val r = repository.getContentByActorName(actorName)) {
                    is Result.Success -> _items.value = r.data
                    is Result.Error -> _items.value = emptyList()
                    Result.Loading -> { }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
