package coinffeine.gui.control.wallet

import scalafx.beans.property.ReadOnlyObjectProperty

import coinffeine.gui.control.NoPopUpContent
import coinffeine.model.currency.{Euro, FiatBalance}

class FiatBalanceWidget(balanceProperty: ReadOnlyObjectProperty[Option[FiatBalance[Euro.type]]])
    extends WalletBalanceWidget(Euro, balanceProperty) with NoPopUpContent
