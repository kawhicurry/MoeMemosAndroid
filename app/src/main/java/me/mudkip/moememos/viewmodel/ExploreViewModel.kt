package me.mudkip.moememos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.mudkip.moememos.data.datasource.EXPLORE_PAGE_SIZE
import me.mudkip.moememos.data.datasource.ExplorePagingSource
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.data.model.MemoComment
import me.mudkip.moememos.data.model.MemoReaction
import me.mudkip.moememos.data.model.MemoSocialSnapshot
import me.mudkip.moememos.data.service.AccountService
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel @Inject constructor(
    private val accountService: AccountService
) : ViewModel() {
    val socialEnabled = accountService.currentAccount
        .map { it is Account.MemosV1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val exploreMemos = accountService.currentAccount
        .flatMapLatest { account ->
            if (account == null || account is Account.Local) {
                return@flatMapLatest flowOf(PagingData.empty<Memo>())
            }

            val remoteRepository = accountService.getRemoteRepository()
                ?: return@flatMapLatest flowOf(PagingData.empty<Memo>())

            Pager(PagingConfig(pageSize = EXPLORE_PAGE_SIZE)) {
                ExplorePagingSource(remoteRepository)
            }.flow
        }
        .cachedIn(viewModelScope)

    suspend fun getMemoSocial(remoteId: String): ApiResponse<MemoSocialSnapshot> {
        val repository = accountService.getRemoteRepository()
            ?: return ApiResponse.Failure.Exception(Exception("Remote account is unavailable"))
        return coroutineScope {
            val comments = async { repository.listMemoComments(remoteId) }
            val reactions = async { repository.listMemoReactions(remoteId) }
            val commentResponse = comments.await()
            if (commentResponse !is ApiResponse.Success) {
                return@coroutineScope when (commentResponse) {
                    is ApiResponse.Failure.Error -> ApiResponse.Failure.Error(commentResponse.payload)
                    is ApiResponse.Failure.Exception -> ApiResponse.Failure.Exception(commentResponse.throwable)
                    is ApiResponse.Success -> error("unreachable")
                }
            }
            val reactionResponse = reactions.await()
            if (reactionResponse !is ApiResponse.Success) {
                return@coroutineScope when (reactionResponse) {
                    is ApiResponse.Failure.Error -> ApiResponse.Failure.Error(reactionResponse.payload)
                    is ApiResponse.Failure.Exception -> ApiResponse.Failure.Exception(reactionResponse.throwable)
                    is ApiResponse.Success -> error("unreachable")
                }
            }
            ApiResponse.Success(
                MemoSocialSnapshot(commentResponse.data, reactionResponse.data)
            )
        }
    }

    suspend fun createMemoComment(remoteId: String, content: String): ApiResponse<MemoComment> {
        val repository = accountService.getRemoteRepository()
            ?: return ApiResponse.Failure.Exception(Exception("Remote account is unavailable"))
        return repository.createMemoComment(remoteId, content)
    }

    suspend fun toggleMemoReaction(
        remoteId: String,
        reactionType: String,
    ): ApiResponse<List<MemoReaction>> {
        val repository = accountService.getRemoteRepository()
            ?: return ApiResponse.Failure.Exception(Exception("Remote account is unavailable"))
        return repository.toggleMemoReaction(remoteId, reactionType)
    }
}
