package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object exchange {
  type AnyExchange[C <: FiatCurrency] = Exchange[C, Exchange.State[C]]
  type NonStartedExchange[C <: FiatCurrency] = Exchange[C, Exchange.NotStarted[C]]
  type HandshakingExchange[C <: FiatCurrency] = Exchange[C, Exchange.Handshaking[C]]
  type RunningExchange[C <: FiatCurrency] = Exchange[C, Exchange.Exchanging[C]]
  type CompletedExchange[C <: FiatCurrency] = Exchange[C, Exchange.Completed[C]]
}
