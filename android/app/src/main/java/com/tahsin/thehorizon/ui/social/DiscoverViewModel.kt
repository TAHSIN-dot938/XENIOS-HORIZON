package com.tahsin.thehorizon.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tahsin.thehorizon.data.auth.AuthException
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.social.Relation
import com.tahsin.thehorizon.data.social.SearchResult
import com.tahsin.thehorizon.data.social.SocialRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    /** True once a search with a valid-length query has completed. */
    val searched: Boolean = false,
    val error: String? = null,
    val busy: Set<String> = emptySet(),
    val message: String? = null,
)

class DiscoverViewModel(private val repo: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()
    private val queries = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val searchJob = viewModelScope.launch {
        queries.debounce(350).distinctUntilChanged().collectLatest { search(it, silent = false) }
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        queries.value = value
    }

    /** Re-run the current search without a spinner (e.g. after friendships changed elsewhere). */
    fun rerun() {
        val q = queries.value
        if (q.trim().length >= 2) viewModelScope.launch { search(q, silent = true) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** "Add" for strangers; "Accept" for people who already asked you (the server auto-accepts). */
    fun addOrAccept(result: SearchResult, onSuccess: () -> Unit) {
        val id = result.user.id
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            try {
                repo.sendRequest(id)
                val next = if (result.relation == Relation.INCOMING) Relation.FRIEND else Relation.OUTGOING
                _state.update { s ->
                    s.copy(results = s.results.map { if (it.user.id == id) it.copy(relation = next) else it })
                }
                onSuccess()
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: AuthException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    private suspend fun search(raw: String, silent: Boolean) {
        val q = raw.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), loading = false, searched = false, error = null) }
            return
        }
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        try {
            val results = repo.search(q)
            _state.update { it.copy(results = results, loading = false, searched = true, error = null) }
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, error = e.message) }
        } catch (e: AuthException) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }
}
