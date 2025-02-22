package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.ViewUtil
import java.lang.Integer.min
import java.util.Currency
import java.util.Locale
import java.util.regex.Pattern

/**
 * A Signal Boost is a one-time ephemeral show of support. Each boost level
 * can unlock a corresponding badge for a time determined by the server.
 */
data class Boost(
  val badge: Badge,
  val price: FiatMoney
) {

  /**
   * A heading containing a 96dp rendering of the boost's badge.
   */
  class HeadingModel(
    val boostBadge: Badge
  ) : PreferenceModel<HeadingModel>() {
    override fun areItemsTheSame(newItem: HeadingModel): Boolean = true

    override fun areContentsTheSame(newItem: HeadingModel): Boolean {
      return super.areContentsTheSame(newItem) && newItem.boostBadge == boostBadge
    }
  }

  /**
   * A widget that allows a user to select from six different amounts, or enter a custom amount.
   */
  class SelectionModel(
    val boosts: List<Boost>,
    val selectedBoost: Boost?,
    val currency: Currency,
    override val isEnabled: Boolean,
    val onBoostClick: (Boost) -> Unit,
    val isCustomAmountFocused: Boolean,
    val onCustomAmountChanged: (String) -> Unit,
    val onCustomAmountFocusChanged: (Boolean) -> Unit,
  ) : PreferenceModel<SelectionModel>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: SelectionModel): Boolean = true

    override fun areContentsTheSame(newItem: SelectionModel): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.boosts == boosts &&
        newItem.selectedBoost == selectedBoost &&
        newItem.currency == currency &&
        newItem.isCustomAmountFocused == isCustomAmountFocused
    }
  }

  private class SelectionViewHolder(itemView: View) : MappingViewHolder<SelectionModel>(itemView) {

    private val boost1: MaterialButton = itemView.findViewById(R.id.boost_1)
    private val boost2: MaterialButton = itemView.findViewById(R.id.boost_2)
    private val boost3: MaterialButton = itemView.findViewById(R.id.boost_3)
    private val boost4: MaterialButton = itemView.findViewById(R.id.boost_4)
    private val boost5: MaterialButton = itemView.findViewById(R.id.boost_5)
    private val boost6: MaterialButton = itemView.findViewById(R.id.boost_6)
    private val custom: AppCompatEditText = itemView.findViewById(R.id.boost_custom)

    private var filter: MoneyFilter? = null

    init {
      custom.filters = emptyArray()
    }

    override fun bind(model: SelectionModel) {
      itemView.isEnabled = model.isEnabled

      model.boosts.zip(listOf(boost1, boost2, boost3, boost4, boost5, boost6)).forEach { (boost, button) ->
        button.isSelected = boost == model.selectedBoost && !model.isCustomAmountFocused
        button.text = FiatMoneyUtil.format(
          context.resources,
          boost.price,
          FiatMoneyUtil.formatOptions()
        )
        button.setOnClickListener {
          model.onBoostClick(boost)
          custom.clearFocus()
        }
      }

      if (filter == null || filter?.currency != model.currency) {
        custom.removeTextChangedListener(filter)

        filter = MoneyFilter(model.currency) {
          model.onCustomAmountChanged(it)
        }

        custom.keyListener = filter
        custom.addTextChangedListener(filter)

        custom.setText("")
      }

      custom.setOnFocusChangeListener { _, hasFocus ->
        model.onCustomAmountFocusChanged(hasFocus)
      }

      if (model.isCustomAmountFocused && !custom.hasFocus()) {
        ViewUtil.focusAndShowKeyboard(custom)
      }
    }
  }

  private class HeadingViewHolder(itemView: View) : MappingViewHolder<HeadingModel>(itemView) {

    private val badgeImageView: BadgeImageView = itemView as BadgeImageView

    override fun bind(model: HeadingModel) {
      badgeImageView.setBadge(model.boostBadge)
    }
  }

  @VisibleForTesting
  class MoneyFilter(val currency: Currency, private val onCustomAmountChanged: (String) -> Unit = {}) : DigitsKeyListener(), TextWatcher {

    val separatorCount = min(1, currency.defaultFractionDigits)
    val prefix: String = "${currency.getSymbol(Locale.getDefault())} "
    val pattern: Pattern = "[0-9]*([.,]){0,$separatorCount}[0-9]{0,${currency.defaultFractionDigits}}".toPattern()

    override fun filter(
      source: CharSequence,
      start: Int,
      end: Int,
      dest: Spanned,
      dstart: Int,
      dend: Int
    ): CharSequence? {

      val result = dest.subSequence(0, dstart).toString() + source.toString() + dest.subSequence(dend, dest.length)
      val resultWithoutCurrencyPrefix = result.removePrefix(prefix)
      val matcher = pattern.matcher(resultWithoutCurrencyPrefix)

      if (!matcher.matches()) {
        return dest.subSequence(dstart, dend)
      }

      return null
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
      if (s.isNullOrEmpty()) return

      val hasPrefix = s.startsWith(prefix)
      if (hasPrefix && s.length == prefix.length) {
        s.clear()
      } else if (!hasPrefix) {
        s.insert(0, prefix)
      }

      onCustomAmountChanged(s.removePrefix(prefix).toString())
    }
  }

  companion object {
    fun register(adapter: MappingAdapter) {
      adapter.registerFactory(SelectionModel::class.java, MappingAdapter.LayoutFactory({ SelectionViewHolder(it) }, R.layout.boost_preference))
      adapter.registerFactory(HeadingModel::class.java, MappingAdapter.LayoutFactory({ HeadingViewHolder(it) }, R.layout.boost_preview_preference))
    }
  }
}
