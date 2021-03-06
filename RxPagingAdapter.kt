/** [PAGING_LOADING_STATE]
 *
 */

enum class PAGING_LOADING_STATE {
    DONE, LOADING, ERROR
}

/**
 * @author Sergey Sh. (GreyLabsDev)
 *
 * @param disposables
 *
 * @property pagingUpdater
 * @property hasFooter
 * @property items
 * @property currentPosition
 * @property loadingState
 * @property itemsChannel
 * @property loadingStateChannel
 */

abstract class RxPagingAdapter<VH : RecyclerView.ViewHolder>(val disposables: CompositeDisposable, private val initialLoad: Boolean = false) : RecyclerView.Adapter<VH>() {

    private var pagingUpdater: RxPagingUpdater? = null
    private var hasFooter = false

    var items: MutableList<Any> = mutableListOf()
    var currentPosition = 0
    var loadingState: PAGING_LOADING_STATE = PAGING_LOADING_STATE.DONE
    var itemsChannel = PublishSubject.create<MutableList<Any>>()
    var loadingStateChannel = PublishSubject.create<PAGING_LOADING_STATE>()

    init {
        initPaging()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is PAGING_LOADING_STATE) {
            PAGING_VIEW_TYPE_FOOTER
        } else PAGING_VIEW_TYPE_DATA
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        if (initialLoad) pagingUpdater?.loadNewItems()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                when (recyclerView.layoutManager) {
                    is LinearLayoutManager -> {
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
                }
            }
        })
    }

    private fun initPaging() {
        subscribeToItemsChannel()
        subscribeToLoadingStateChannel()
    }

    private fun subscribeToItemsChannel() {
        itemsChannel.let { channel ->
            disposables += channel.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { newItems ->
                        addItems(newItems)
                    }, {}, {}
                )
        }
    }

    private fun subscribeToLoadingStateChannel() {
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
        when (state) {
            PAGING_LOADING_STATE.LOADING -> {
                addLoadingFooter(state)
            }
            PAGING_LOADING_STATE.ERROR -> {
                addErrorFooter(state)
            }
            PAGING_LOADING_STATE.DONE -> {
                removeFooter()
            }
        }
        loadingState = state
    }

    private fun addLoadingFooter(state: PAGING_LOADING_STATE) {
        val footerPosition = items.size - 1
        hasFooter = true
        if (footerPosition < 0) {
            addItem(state)
        } else {
            getItem(footerPosition)?.let { item ->
                if (item is PAGING_LOADING_STATE) {
                    items[footerPosition] = state
                    notifyItemChanged(footerPosition)
                } else addItem(state)
            }
        }
    }

    private fun addErrorFooter(state: PAGING_LOADING_STATE) {
        val footerPosition = items.size - 1
        hasFooter = true
        if (footerPosition < 0) {
            addItem(state)
        } else {
            getItem(footerPosition)?.let {
                items[footerPosition] = state
                notifyItemChanged(footerPosition)
            }
        }
    }

    private fun removeFooter() {
        val footerPosition = items.size - 1
        if (hasFooter) {
            hasFooter = false
            getItem(footerPosition)?.let { footerItem ->
                items.remove(footerItem)
                notifyItemRemoved(footerPosition)
            }
        }
    }

    /**[setPagingUpdater]
     * @param updater
     */

    fun setPagingUpdater(updater: RxPagingUpdater?) {
        pagingUpdater = updater
    }

    /**[clearItemsAndReload]
     * Resetting adapter and its updater to initial state
     * and loading items from pagination start
     */

    fun clearItemsAndReload() {
        items.clear()
        notifyDataSetChanged()
        pagingUpdater?.resetPosition()
        pagingUpdater?.loadNewItems()
    }

    fun clearItems() {
        items.clear()
        notifyDataSetChanged()
    }

    /**[addItems]
     * Adding item to end of current items list with adapter notification
     * @param newItem
     */

    fun addItem(newItem: Any) {
        if (items.contains(newItem).not()) {
            items.add(newItem)
            notifyItemInserted(items.size - 1)
        }
    }

    /**[addItems]
     * Adding pack of item to end of current items list with adapter notification
     * @param newItems
     */

    fun addItems(newItems: MutableList<Any>) {
        if (items.intersect(newItems).size != newItems.size) {
            val startPosition = itemCount
            items.addAll(newItems)
            notifyItemRangeInserted(startPosition, newItems.size)
        }
    }

    /**[insertItem]
     * Inserting item to defined position with adapter notification
     * @param newItem
     * @param position
     */

    fun insertItem(newItem: Any, position: Int) {
        if (items.contains(newItem).not()) {
            items.add(position, newItem)
            notifyItemInserted(position)
        }
    }

    /**[getItem]
     * Returns item of adapter at current position
     */

    fun getItem(position: Int): Any? {
        return if (items.lastIndex >= position) items[position] else null
    }

    /**[removeItemAt]
     * Removing item item from defined position with adapter notification
     * and calling [RxPagingUpdater.showPlaceholder] from pagingAdapter - if you are using PlaceholderSwitcher,
     * it will show placeholder after last item will be deleted
     * @param position
     */

    fun removeItemAt(position: Int) {
        if (loadingState == PAGING_LOADING_STATE.DONE) {
            items.removeAt(position)
            notifyItemRemoved(position)
            if (items.size == 0) {
                pagingUpdater?.showPlaceholder()
            }
        }
    }

    /**[RxPagingUpdater]
     * @property isFirstLoad
     * @property offset
     * @property count
     * @property placeholderSwitcher
     * @property currentPosition
     * @property isReachedEndOfList
     */

    abstract class RxPagingUpdater {
        private var isFirstLoad = true
        private var offset: Int = 0
        private var count: Int = 0

        var placeholderSwitcher: PlaceholderSwitcher? = null
        var currentPosition = offset
        var isReachedEndOfList = false

        /**
         * [setup]
         * Method for initial setup of just created updater
         * @param offset
         * @param count
         * @param placeholderSwitcher
         */

        fun setup(offset: Int? = null, count: Int? = null, placeholderSwitcher: PlaceholderSwitcher? = null) {
            offset?.let {
                this.offset = it
                if (isFirstLoad) {
                    currentPosition = offset
                }
            }
            count?.let {
                this.count = it
            }
            placeholderSwitcher?.let {
                this.placeholderSwitcher = it
            }
        }

        /**[getCount]
         * Returns one page items count
         */

        fun getCount(): Int {
            return count
        }

        /**[resetPosition]
         * Uses to reset state of rxUpdater to initial
         */

        fun resetPosition() {
            currentPosition = 0
            isReachedEndOfList = false
        }

        /**
         * [loadNewItems]
         * Realization must contain:
         * - itemsChannel, loadingStateChannel from created RxPagingAdapter
         * - calls onNext of loadingStateChannel when updater is loading, loaded items or catched error
         *   it needs to support changing loading states and adding appropriate footer to recyclerView
         * - calls onNext of itemsChannel to send new items to RxPagingAdapter
         * - calls for updating current position after successful loading new items
         * */

        abstract fun loadNewItems()

        /**[showPlaceholder]
         * Calls same named method from [placeholderSwitcher] if it is not null
         */

        fun showPlaceholder() {
            placeholderSwitcher?.showPlaceholder()
        }

        /**[showPlaceholder]
         * Calls same named method from [placeholderSwitcher] if it is not null
         */

        fun hidePlaceholder() {
            placeholderSwitcher?.hidePlaceholder()
        }

        /**[updateCurrentPosition]
         * Call this method in your rxUpdater when it loads new pack of items, it will update paging position
         * or switch to [isReachedEndOfList] state (this flag uses to avoid unnecessary calls of [loadNewItems] method)
         * @param itemsLoaded
         */

        fun updateCurrentPosition(itemsLoaded: Int) {
            if (itemsLoaded < count) {
                isReachedEndOfList = true
            }
            currentPosition += itemsLoaded
        }
    }

    /**[PlaceholderSwitcher]
     * Not necessary interface, but you can realize it and add to updater in [RxPagingUpdater.setup] method
     * if you need to switch your placeholder state in case when updater loads empty or null list of items
     */

    interface PlaceholderSwitcher {
        /**[showPlaceholder]
         * Implement this method with your logic for showing placeholder
         */
        fun showPlaceholder()

        /**[hidePlaceholder]
         * Implement this method with your logic for hiding placeholder
         */
        fun hidePlaceholder()
    }
}
