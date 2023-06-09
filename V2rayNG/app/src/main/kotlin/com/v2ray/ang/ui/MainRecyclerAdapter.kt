package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Color
import kotlin.concurrent.thread
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.service.ApiService
import com.v2ray.ang.service.GetDataRequest
import com.v2ray.ang.service.GetDataResponse
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class MainRecyclerAdapter(val activity: MainActivity) :
    RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var mActivity: MainActivity = activity
    private lateinit var myThread: Thread
    private val handler = Handler(Looper.getMainLooper())

    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    lateinit var txtUpdate: TextView
    private val share_method: Array<out String> by lazy {
        mActivity.resources.getStringArray(R.array.share_method)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setUsagesFunction(position: Int, text: String, expire: Long) {
        println(expire)
        mActivity.mainViewModel.serversCache[position].tapUsage = text


        val unixTimestampMillis = 1688225002000L

        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(unixTimestampMillis),
            ZoneId.systemDefault()
        )

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDate = dateTime.format(formatter)

        mActivity.mainViewModel.serversCache[position].tapExpire = formattedDate
        notifyItemChanged(position)
    }

    var isRunning = false

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size + 1

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {


        if (holder is MainViewHolder) {
            val guid = mActivity.mainViewModel.serversCache[position].guid
            val config = mActivity.mainViewModel.serversCache[position].config
            val tvUse = mActivity.mainViewModel.serversCache[position].tapUsage

            val item = mActivity.mainViewModel.serversCache[position].tapUsage
            val itemExpire = mActivity.mainViewModel.serversCache[position].tapExpire

            println("______________________________________________")
            println("_______________________${guid}_______________________")
            println("______________________________________________")

            holder.itemMainBinding.tvUsages.text = item
            holder.itemMainBinding.tvExpires.text = itemExpire

//            //filter
//            if (mActivity.mainViewModel.subscriptionId.isNotEmpty()
//                && mActivity.mainViewModel.subscriptionId != config.subscriptionId
//            ) {
//                holder.itemMainBinding.cardView.visibility = View.GONE
//            } else {
//                holder.itemMainBinding.cardView.visibility = View.VISIBLE
//            }

            val outbound = config.getProxyOutbound()
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.itemMainBinding.tvName.text = config.remarks
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString() ?: ""
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.itemMainBinding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        mActivity,
                        R.color.colorPingRed
                    )
                )
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        mActivity,
                        R.color.colorPing
                    )
                )
            }
            if (guid == mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorSelected)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorUnselected)
            }
            holder.itemMainBinding.tvSubscription.text = ""
            val json = subStorage?.decodeString(config.subscriptionId)
            if (!json.isNullOrBlank()) {
                val sub = Gson().fromJson(json, SubscriptionItem::class.java)
                holder.itemMainBinding.tvSubscription.text = sub.remarks
            }

            var shareOptions = share_method.asList()
            when (config.configType) {
                EConfigType.CUSTOM -> {
                    holder.itemMainBinding.tvType.text =
                        mActivity.getString(R.string.server_customize_config)
                    shareOptions = shareOptions.takeLast(1)
                }
                EConfigType.VLESS -> {
                    holder.itemMainBinding.tvType.text = config.configType.name
                }
                else -> {
                    holder.itemMainBinding.tvType.text = config.configType.name.lowercase()
                }
            }


//            TODO : check Inbound
            holder.itemMainBinding.tvStatistics.text =
                "${outbound?.getServerAddress()} : ${outbound?.getServerPort()}"
//            holder.itemMainBinding.tvStatistics.text = "dfdfdfdfdfdfdfd"

            holder.itemMainBinding.layoutShare.setOnClickListener {
                AlertDialog.Builder(mActivity).setItems(shareOptions.toTypedArray()) { _, i ->
                    try {
                        when (i) {
                            0 -> {
                                if (config.configType == EConfigType.CUSTOM) {
                                    shareFullContent(guid)
                                } else {
                                    val ivBinding =
                                        ItemQrcodeBinding.inflate(LayoutInflater.from(mActivity))
                                    ivBinding.ivQcode.setImageBitmap(
                                        AngConfigManager.share2QRCode(
                                            guid
                                        )
                                    )
                                    AlertDialog.Builder(mActivity).setView(ivBinding.root).show()
                                }
                            }
                            1 -> {
                                if (AngConfigManager.share2Clipboard(mActivity, guid) == 0) {
                                    mActivity.toast(R.string.toast_success)
                                } else {
                                    mActivity.toast(R.string.toast_failure)
                                }
                            }
                            2 -> shareFullContent(guid)
                            else -> mActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.show()
            }




            holder.itemMainBinding.layoutUpdate.setOnClickListener {
                val text = holder.itemMainBinding.tvName.text.toString()
                val parts = text.split("-")
                val email = parts.last()


                val password = "${outbound?.getPassword()}"
                val domain = "${outbound?.getServerAddress()}"
//                println(domain)
//                val selected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
//                if (guid != selected) {
//                    mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
//                    if (!TextUtils.isEmpty(selected)) {
//                        notifyItemChanged(mActivity.mainViewModel.getPosition(selected!!))
//                    }
//                    notifyItemChanged(mActivity.mainViewModel.getPosition(guid))
//                }
                handleButtonClick(text, email, password, position, guid, domain)

                println(tvUse)




                println("${holder.itemMainBinding.tvName.text.toString()} | $guid |  ${position} |||")

            }


            holder.itemMainBinding.layoutEdit.setOnClickListener {
                val intent = Intent().putExtra("guid", guid)
                    .putExtra("isRunning", isRunning)
                if (config.configType == EConfigType.CUSTOM) {
                    mActivity.startActivity(
                        intent.setClass(
                            mActivity,
                            ServerCustomConfigActivity::class.java
                        )
                    )
                } else {
                    mActivity.startActivity(intent.setClass(mActivity, ServerActivity::class.java))
                }
            }


            holder.itemMainBinding.layoutRemove.setOnClickListener {
                if (guid != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
                    if (settingsStorage?.decodeBool(AppConfig.PREF_CONFIRM_REMOVE) == true) {
                        AlertDialog.Builder(mActivity).setMessage(R.string.del_config_comfirm)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                removeServer(guid, position)
                            }
                            .show()
                    } else {
                        removeServer(guid, position)
                    }
                }
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                val selected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
                if (guid != selected) {
                    mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                    if (!TextUtils.isEmpty(selected)) {
                        notifyItemChanged(mActivity.mainViewModel.getPosition(selected!!))
                    }
                    notifyItemChanged(mActivity.mainViewModel.getPosition(guid))
                    if (isRunning) {
                        mActivity.showCircle()
                        Utils.stopVService(mActivity)
                        Observable.timer(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                V2RayServiceManager.startV2Ray(mActivity)
                                mActivity.hideCircle()
                            }
                    }
                }
            }


        }
        if (holder is FooterViewHolder) {
            //if (activity?.defaultDPreference?.getPrefBoolean(AppConfig.PREF_INAPP_BUY_IS_PREMIUM, false)) {
            if (true) {
                holder.itemFooterBinding.layoutEdit.visibility = View.INVISIBLE
            } else {
                holder.itemFooterBinding.layoutEdit.setOnClickListener {
                    Utils.openUri(
                        mActivity,
                        "${Utils.decode(AppConfig.promotionUrl)}?t=${System.currentTimeMillis()}"
                    )
                }
            }
        }
    }

    private fun shareFullContent(guid: String) {
        if (AngConfigManager.shareFullContent2Clipboard(mActivity, guid) == 0) {
            mActivity.toast(R.string.toast_success)
        } else {
            mActivity.toast(R.string.toast_failure)
        }
    }

    private fun removeServer(guid: String, position: Int) {
        mActivity.mainViewModel.removeServer(guid)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, mActivity.mainViewModel.serversCache.size)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateUsage(
        total: Long,
        usage: Long,
        expire: Long,
        guid: String,
        position: Int,
        email: String,
        password: String
    ) {
        //TODO : check updateusage
        val decimalFormat = DecimalFormat("#.##")
        decimalFormat.maximumFractionDigits = 2
        var vollTotal = ""


        if (total == 0L) {
            vollTotal = "Unlimited"
        } else {
            val resultTotal = checkFileSize(total)
            val convertedTotalNumber = resultTotal.first
            val Totalunit = resultTotal.second
            vollTotal = "${decimalFormat.format(convertedTotalNumber)} ${Totalunit}"
        }

        val resultUsage = checkFileSize(usage)
        val convertedUsageNumber = resultUsage.first
        val Usageunit = resultUsage.second
        val vollUsage = "${decimalFormat.format(convertedUsageNumber)} ${Usageunit}"

        var VollUsages = "$vollUsage / $vollTotal"


        setUsagesFunction(position, "$VollUsages", expire)

        mActivity.mainViewModel.updateServer(total, usage, email, password, position)


//        mActivity.setUsage("$VollUsages")
    }

    private fun dontUseRadepa(
    ) {
        mActivity.showMessage("Your config is not created with radepa-xui panel")
    }

    private fun sendWaitMessage(
    ) {
        mActivity.showMessage("Please Wait a minute ...")
    }

    //TODO: Check this func
    private fun handleButtonClick(
        client: String,
        email: String,
        password: String,
        position: Int,
        guid: String,
        domain: String
    ) {


        val url = "$domain"

        val text1 = "$client"






        if (hasHyphen(text1)) {

            sendWaitMessage()

            myThread = Thread {
                val result = sendRequestToMultiplePorts(url)

                if (result != null) {
                    println("Processing complete")

                    val retrofit = Retrofit.Builder()
                        .baseUrl("$result/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val apiService = retrofit.create(ApiService::class.java)

                    val getDataReq = GetDataRequest(email, password)
                    val call = apiService.login(getDataReq)

                    call.enqueue(object : retrofit2.Callback<GetDataResponse> {
                        @RequiresApi(Build.VERSION_CODES.O)
                        override fun onResponse(
                            call: retrofit2.Call<GetDataResponse>,
                            response: retrofit2.Response<GetDataResponse>
                        ) {
                            if (response.isSuccessful) {
                                val getDataRequest = response.body()
                                val downValue = getDataRequest?.obj?.down
                                val upValue = getDataRequest?.obj?.up


                                val sum = downValue?.plus(upValue!!)
                                val totalUsage = getDataRequest?.obj?.total
                                val expire = getDataRequest?.obj?.expiryTime


                                println("Response: ${getDataRequest}")

                                if (totalUsage != null) {
                                    if (sum != null) {
                                        if (expire != null) {
                                            updateUsage(
                                                totalUsage,
                                                sum,
                                                expire,
                                                guid,
                                                position,
                                                email,
                                                password
                                            )
                                        } else {
                                            dontUseRadepa()
                                        }
                                    } else {
                                        dontUseRadepa()

                                    }
                                } else {
                                    dontUseRadepa()

                                }

                            } else {
                                println("Failed to login: ${response.code()}")
                            }
                        }

                        override fun onFailure(
                            call: retrofit2.Call<GetDataResponse>,
                            t: Throwable
                        ) {
                            println("Failed to login: ${t.message}")
                        }
                    })

                } else {
                    dontUseRadepa()
                }
                handler.post {
                    // Code to be executed on the main thread
                    println("Thread has finished")
                }
            }
            myThread.start()
        } else {
            dontUseRadepa()
        }

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(
                    ItemRecyclerMainBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            else ->
                FooterViewHolder(
                    ItemRecyclerFooterBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == mActivity.mainViewModel.serversCache.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }


//        fun bind(card: CardItem) {
//            textView.text = card.text
//        }

    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemDismiss(position: Int) {
        val guid = mActivity.mainViewModel.serversCache.getOrNull(position)?.guid ?: return
        if (guid != mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)) {
//            mActivity.alert(R.string.del_config_comfirm) {
//                positiveButton(android.R.string.ok) {
            mActivity.mainViewModel.removeServer(guid)
            notifyItemRemoved(position)
//                }
//                show()
//            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mActivity.mainViewModel.swapServer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        // position is changed, since position is used by click callbacks, need to update range
        if (toPosition > fromPosition)
            notifyItemRangeChanged(fromPosition, toPosition - fromPosition + 1)
        else
            notifyItemRangeChanged(toPosition, fromPosition - toPosition + 1)
        return true
    }

    override fun onItemMoveCompleted() {
        // do nothing
    }

    fun checkFileSize(number: Long): Pair<Double, String> {
        val absNumber = Math.abs(number.toDouble())

        val unit: String = when {
            absNumber < 1024 -> "bytes"
            absNumber < 1024 * 1024 -> "KB"
            absNumber < 1024 * 1024 * 1024 -> "MB"
            else -> "GB"
        }

        val convertedNumber =
            absNumber / Math.pow(1024.0, listOf("bytes", "KB", "MB", "GB").indexOf(unit).toDouble())

        return Pair(convertedNumber, unit)
    }

    fun convertUnixToHumanDate(unixTimestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = Date(unixTimestamp * 1000) // Multiply by 1000 to convert seconds to milliseconds
        return dateFormat.format(date)
    }


    fun sendGet(): MutableList<String> {
        val url = "rade1.goldaccess.xyz"

        val ports = listOf(443, 2087, 2086, 2083, 2082)
        val httpClient = OkHttpClient()
        var currentResult: String? = null // Variable to store the result
        var urlNue = ""
        val results = mutableListOf<String>()
        for (port in ports) {
            val protocol = if (port == 443 || port == 2087 || port == 2083) "https" else "http"
            val requestUrl = "$protocol://$url:$port"
            val request = Request.Builder()
                .url(requestUrl)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    if (response.code() == 200) {
                        currentResult = requestUrl // Store the result in the variable
                    }
                    response.close()
                }
            })

            // Add delay or other waiting mechanism if needed
            Thread.sleep(5000) // Wait for the requests to complete

            currentResult?.let {
                results.add(it) // Add the result to the results list
            }
        }

        return results
    }


    fun sendRequestToMultiplePorts(url: String): String? {
        val ports = listOf(443, 2087, 2086, 2083, 2082)
        val httpClient = OkHttpClient()
        var currentDomain: String? = null

        for (port in ports) {
            val protocol = if (port == 443 || port == 2087 || port == 2083) "https" else "http"
            val requestUrl = "$protocol://$url:$port"
            val request = Request.Builder()
                .url(requestUrl)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    // Handle request failure
                    println("Request failed for $requestUrl")
                    e.printStackTrace()
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    if (response.code() == 200) {
                        currentDomain = requestUrl // Store the current domain in the variable
                    }
                    response.close()
                }
            })
        }

        // Add delay or other waiting mechanism if needed
        Thread.sleep(5000) // Wait for the requests to complete

        return currentDomain
    }


    fun hasHyphen(text: String): Boolean {
        return text.contains("-")
    }
}
