enum class PAGING_LOADING_STATE {
    DONE, LOADING, ERROR
}

abstract class RxPagingAdapter<VH : RecyclerView.ViewHolder>(val disposables: CompositeDisposable) : RecyclerView.Adapter<VH>() {

    //TODO 1. Add all comments
    //TODO 2. Find way to replace PublishSubjects with some Observables
    //TODO 3. Add simple example (?)

    private var pagingUpdater: RxPagingUpdater? = null
    private var hasFooter = false

    var items: MutableList<Any> = mutableListOf()
    var currentPosition = 0

    var itemsChannel = PublishSubject.create<MutableList<Any>>()
    var loadingStateChannel = PublishSubject.create<PAGING_LOADING_STATE>()

    var loadingState: PAGING_LOADING_STATE = PAGING_LOADING_STATE.DONE

    init {
        initPaging()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is PAGING_LOADING_STATE) {
            VIEW_TYPE_FOOTER
        } else VIEW_TYPE_DATA
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        pagingUpdater?.loadNewItems()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val lastVisibleItemPosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                if (lastVisibleItemPosition == itemCount - 1) {
                    loadingState = PAGING_LOADING_STATE.LOADING
                    pagingUpdater?.apply {
                        if (isReachedEndOfList.not()) {
                            loadNewItems()
                        } else updateLoadingState(PAGING_LOADING_STATE.DONE)
                    } ?: updateLoadingState(PAGING_LOADING_STATE.DONE)
                }
            }
        })
    }

    private fun initPaging() {
        itemsChannel.let { channel ->
            disposables += channel.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ newItems ->
                        addItems(newItems)
                    }, {}, {})
        }
        loadingStateChannel.let { channel ->
            disposables += channel.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { newLoadingState ->
                                updateLoadingState(newLoadingState)
                            }, {}, {}
                    )
        }
    }

    private fun updateLoadingState(state: PAGING_LOADING_STATE) {
        val footerPosition = items.size - 1
        when (state) {
            PAGING_LOADING_STATE.LOADING -> {
                hasFooter = true
                if (footerPosition < 0) {
                    addItem(state)
                } else {
                    getItem(footerPosition)?.let {item ->
                        if (item is PAGING_LOADING_STATE) {
                            items[footerPosition] = state
                            notifyItemChanged(footerPosition)
                        } else addItem(state)
                    }
                }
            }
            PAGING_LOADING_STATE.ERROR -> {
                hasFooter = true
                getItem(footerPosition)?.let {
                    items[footerPosition] = state
                    notifyItemChanged(footerPosition)
                }
            }
            PAGING_LOADING_STATE.DONE -> {
                if (hasFooter) {
                    hasFooter = false
                    getItem(footerPosition)?.let { footerItem ->
                        items.remove(footerItem)
                        notifyItemRemoved(footerPosition)
                    }
                }
            }
        }
        loadingState = state
    }

    fun setPagingUpdater(updater: RxPagingUpdater?) {
        pagingUpdater = updater
    }

    fun clearItemsAndReload() {
        items.clear()
        notifyDataSetChanged()
        pagingUpdater?.resetPosition()
        pagingUpdater?.loadNewItems()
    }

    fun addItem(newItem: Any) {
        if (items.contains(newItem).not()) {
            items.add(newItem)
            notifyItemInserted(items.size - 1)
        }
    }

    fun addItems(newItems: MutableList<Any>) {
        if (items.intersect(newItems).size != newItems.size) {
            val startPosition = itemCount
            items.addAll(newItems)
            notifyItemRangeInserted(startPosition, newItems.size)
        }
    }

    fun insertItem(newItem: Any, position: Int) {
        if (items.contains(newItem).not()) {
            items.add(position, newItem)
            notifyItemInserted(position)
        }
    }

    fun getItem(position: Int): Any? {
        return if (items.lastIndex >= position) items[position] else null
    }

    fun removeItemAt(position: Int) {
        if (loadingState == PAGING_LOADING_STATE.DONE) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    abstract class RxPagingUpdater {
        private var firstLoad = true

        private var offset: Int = 0
        private var count: Int = 0

        var currentPosition = offset
        var isReachedEndOfList = false

        fun setup(offset: Int? = null, count: Int? = null) {
            offset?.let {
                this.offset = it
                if (firstLoad) {
                    currentPosition = offset
                }
            }
            count?.let {
                this.count = it
            }
        }

        fun getCount(): Int {
            return count
        }

        fun resetPosition() {
            currentPosition = 0
        }

        /**
         * @loadNewItems
         * Realization must contain:
         * - itemsChannel, loadingStateChannel from created RxPagingAdapter
         * - calls onNext of loadingStateChannel when updater is loading, loaded items or catched error
         *   it needs to support changing loading states and adding appropriate footer to recyclerView
         * - calls onNext of itemsChannel to send new items to RxPagingAdapter
         * - calls for updating current position after successful loading new items
         * */

        abstract fun loadNewItems()

        fun updateCurrentPosition(itemsLoaded: Int) {
            if (itemsLoaded < count) {
                isReachedEndOfList = true
            }
            currentPosition += itemsLoaded
        }
    }
}
