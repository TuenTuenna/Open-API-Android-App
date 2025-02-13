package com.codingwithmitch.openapi.repository.main

import android.util.Log
import androidx.lifecycle.LiveData
import com.codingwithmitch.openapi.util.ApiSuccessResponse
import com.codingwithmitch.openapi.util.GenericApiResponse
import com.codingwithmitch.openapi.api.main.OpenApiMainService
import com.codingwithmitch.openapi.api.main.network_responses.BlogCreateUpdateResponse
import com.codingwithmitch.openapi.models.AuthToken
import com.codingwithmitch.openapi.models.BlogPost
import com.codingwithmitch.openapi.persistence.BlogPostDao
import com.codingwithmitch.openapi.repository.JobManager
import com.codingwithmitch.openapi.repository.NetworkBoundResource
import com.codingwithmitch.openapi.session.SessionManager
import com.codingwithmitch.openapi.ui.DataState
import com.codingwithmitch.openapi.ui.Response
import com.codingwithmitch.openapi.ui.ResponseType
import com.codingwithmitch.openapi.ui.main.create_blog.state.CreateBlogViewState
import com.codingwithmitch.openapi.util.AbsentLiveData
import com.codingwithmitch.openapi.util.DateUtils
import com.codingwithmitch.openapi.util.SuccessHandling.Companion.RESPONSE_MUST_BECOME_CODINGWITHMITCH_MEMBER
import kotlinx.coroutines.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject

class CreateBlogRepository
@Inject
constructor(
    val openApiMainService: OpenApiMainService,
    val blogPostDao: BlogPostDao,
    val sessionManager: SessionManager
)
{
    private val TAG: String = "AppDebug"

    private val jobManager: JobManager = JobManager()

    fun createNewBlogPost(
        authToken: AuthToken,
        title: RequestBody,
        body: RequestBody,
        image: MultipartBody.Part?
    ): LiveData<DataState<CreateBlogViewState>> {
        return object: NetworkBoundResource<BlogCreateUpdateResponse, BlogPost, CreateBlogViewState>(
            "createNewBlogPost",
            sessionManager.isConnectedToTheInternet(),
            true,
            true,
            false
        ){

            // not applicable
            override suspend fun createCacheRequestAndReturn() {

            }

            override suspend fun handleApiSuccessResponse(response: ApiSuccessResponse<BlogCreateUpdateResponse>) {

                // If they don't have a paid membership account it will still return a 200
                // Need to account for that
                if(!response.body.response.equals(RESPONSE_MUST_BECOME_CODINGWITHMITCH_MEMBER)){
                    val updatedBlogPost = BlogPost(
                        response.body.pk,
                        response.body.title,
                        response.body.slug,
                        response.body.body,
                        response.body.image,
                        DateUtils.convertServerStringDateToLong(response.body.date_updated),
                        response.body.username
                    )
                    updateLocalDb(updatedBlogPost)
                }

                withContext(Dispatchers.Main){
                    // finish with success response
                    onCompleteJob(
                        DataState.data(
                            null,
                            Response(response.body.response, ResponseType.Dialog())
                        ))
                }
            }

            override fun createCall(): LiveData<GenericApiResponse<BlogCreateUpdateResponse>> {
                return openApiMainService.createBlog(
                    "Token ${authToken.token!!}",
                    title,
                    body,
                    image
                )
            }

            // not applicable
            override fun loadFromCache(): LiveData<CreateBlogViewState> {
                return AbsentLiveData.create()
            }

            override suspend fun updateLocalDb(cacheObject: BlogPost?) {
                cacheObject?.let{
                    blogPostDao.insert(it)
                }
            }

            override fun setJob(job: Job) {
                jobManager.addJob(methodName, job)
            }

        }.asLiveData()
    }


    fun cancelActiveJobs(){
        Log.d(TAG, "CreateBlogRepository: cancelling on-going jobs... ")
        jobManager.cancelActiveJobs()
    }

}


























