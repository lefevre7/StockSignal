package com.example.stocksignal.ui.components

object MetricExplanations {
    
    const val RSI_14 = """
        <strong>RSI 14 (Relative Strength Index)</strong><br/><br/>
        
        Measures momentum on a scale of 0-100.<br/><br/>
        
        <strong>Typical Ranges:</strong><br/>
        • 70-100: Overbought (may reverse down)<br/>
        • 30-0: Oversold (may reverse up)<br/>
        • 40-60: Neutral zone<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        Above 70 suggests the stock may be overvalued and due for a pullback. 
        Below 30 suggests it may be undervalued and due for a bounce.
    """
    
    const val MACD = """
        <strong>MACD (Moving Average Convergence Divergence)</strong><br/><br/>
        
        Shows the relationship between two moving averages of a stock's price.<br/><br/>
        
        <strong>What it Measures:</strong><br/>
        Trend direction and momentum strength.<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        • Positive MACD: Bullish momentum<br/>
        • Negative MACD: Bearish momentum<br/>
        • When MACD crosses above signal line: Buy signal<br/>
        • When MACD crosses below signal line: Sell signal
    """
    
    const val MACD_SIGNAL = """
        <strong>MACD Signal Line</strong><br/><br/>
        
        A 9-period exponential moving average of the MACD line.<br/><br/>
        
        <strong>How to Use:</strong><br/>
        Acts as a trigger for buy and sell signals. When the MACD line crosses 
        above the signal line, it's a bullish signal. When it crosses below, 
        it's a bearish signal.
    """
    
    const val MACD_HISTOGRAM = """
        <strong>MACD Histogram</strong><br/><br/>
        
        The difference between the MACD line and the signal line.<br/><br/>
        
        <strong>What it Shows:</strong><br/>
        • Positive bars: MACD above signal (bullish)<br/>
        • Negative bars: MACD below signal (bearish)<br/>
        • Growing bars: Momentum increasing<br/>
        • Shrinking bars: Momentum decreasing
    """
    
    const val SMA_50 = """
        <strong>SMA 50 (50-period Simple Moving Average)</strong><br/><br/>
        
        The average price over the last 50 periods.<br/><br/>
        
        <strong>What it Indicates:</strong><br/>
        Medium-term trend direction.<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        • Price above SMA 50: Bullish trend<br/>
        • Price below SMA 50: Bearish trend<br/>
        • SMA 50 crossing above SMA 200: "Golden Cross" (very bullish)<br/>
        • SMA 50 crossing below SMA 200: "Death Cross" (very bearish)
    """
    
    const val SMA_200 = """
        <strong>SMA 200 (200-period Simple Moving Average)</strong><br/><br/>
        
        The average price over the last 200 periods.<br/><br/>
        
        <strong>What it Indicates:</strong><br/>
        Long-term trend direction. Widely watched by institutional investors.<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        • Price above SMA 200: Long-term uptrend<br/>
        • Price below SMA 200: Long-term downtrend<br/>
        • Acts as strong support in uptrends<br/>
        • Acts as strong resistance in downtrends
    """
    
    const val ATR_14 = """
        <strong>ATR 14 (Average True Range)</strong><br/><br/>
        
        Measures market volatility over the last 14 periods.<br/><br/>
        
        <strong>What it Shows:</strong><br/>
        Average price movement range per period.<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        • High ATR: High volatility (wider price swings)<br/>
        • Low ATR: Low volatility (narrow price ranges)<br/>
        • Use for setting stop-loss levels<br/>
        • High ATR may mean higher risk/reward
    """
    
    const val VOLUME_ZSCORE = """
        <strong>Volume Z-Score</strong><br/><br/>
        
        Measures how unusual current trading volume is compared to historical averages.<br/><br/>
        
        <strong>Typical Ranges:</strong><br/>
        • +2 to +3: Very high volume (2-3x normal)<br/>
        • -2 to +2: Normal range<br/>
        • Below -2: Very low volume<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        High positive z-score indicates unusual buying/selling activity, 
        which often accompanies major price moves or news events.
    """
    
    const val RETURN_ZSCORE = """
        <strong>Rolling Return Z-Score</strong><br/><br/>
        
        Measures how unusual recent price returns are compared to historical patterns.<br/><br/>
        
        <strong>Typical Ranges:</strong><br/>
        • Above +2: Unusually large gains<br/>
        • -2 to +2: Normal range<br/>
        • Below -2: Unusually large losses<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        Extreme values often precede reversals due to mean reversion. 
        Very high or low values may indicate overextended moves.
    """
    
    const val BOLLINGER_BANDS = """
        <strong>Bollinger Bands</strong><br/><br/>
        
        Volatility bands placed above and below a moving average.<br/><br/>
        
        <strong>Components:</strong><br/>
        • Middle band: 20-period SMA<br/>
        • Upper band: SMA + (2 × standard deviation)<br/>
        • Lower band: SMA - (2 × standard deviation)<br/><br/>
        
        <strong>Trading Implications:</strong><br/>
        • Price near upper band: Potentially overbought<br/>
        • Price near lower band: Potentially oversold<br/>
        • Bands widen: Increasing volatility<br/>
        • Bands narrow: Decreasing volatility (potential breakout coming)
    """

    const val MARKET_CAP = """
        <strong>Market Cap</strong><br/><br/>
        
        Total market value of all outstanding shares (share price x shares outstanding).<br/><br/>
        
        <strong>Why it matters:</strong><br/>
        • Larger caps tend to be more stable<br/>
        • Smaller caps can be more volatile with higher growth potential
    """

    const val PE_RATIO = """
        <strong>P/E Ratio (Price-to-Earnings)</strong><br/><br/>
        
        Shows how much investors are paying for $1 of earnings.<br/><br/>
        
        <strong>How to read it:</strong><br/>
        • Higher P/E can signal growth expectations or overvaluation<br/>
        • Lower P/E can signal value or slower growth<br/>
        • Negative P/E means the company is unprofitable
    """

    const val DIVIDEND_YIELD = """
        <strong>Dividend Yield</strong><br/><br/>
        
        Annual dividend payments as a percentage of the current share price.<br/><br/>
        
        <strong>How to read it:</strong><br/>
        • Higher yields provide more income<br/>
        • Extremely high yields can signal payout risk
    """

    const val WEEK_52_HIGH = """
        <strong>52-Week High</strong><br/><br/>
        
        The highest trading price over the last 52 weeks (1 year).<br/><br/>
        
        <strong>How to use it:</strong><br/>
        • Near the high: strong momentum or potential resistance
    """

    const val WEEK_52_LOW = """
        <strong>52-Week Low</strong><br/><br/>
        
        The lowest trading price over the last 52 weeks (1 year).<br/><br/>
        
        <strong>How to use it:</strong><br/>
        • Near the low: weakness or potential support
    """
    
    // Signal tier explanations
    const val STRONG_BUY = """
        <strong>Strong Buy (Score: 60-100)</strong><br/><br/>
        
        High confidence buy signal with multiple positive indicators aligned.<br/><br/>
        
        <strong>What this means:</strong><br/>
        Multiple technical indicators are showing bullish signals. The stock has 
        strong momentum, favorable trend direction, and supportive volume patterns.<br/><br/>
        
        <strong>Typical characteristics:</strong><br/>
        • RSI showing strength but not overbought<br/>
        • MACD trending positive<br/>
        • Price above key moving averages<br/>
        • Strong relative volume<br/>
        • High confidence score (70%+)
    """
    
    const val BUY = """
        <strong>Buy (Score: 30-59)</strong><br/><br/>
        
        Moderate buy signal with some positive indicators.<br/><br/>
        
        <strong>What this means:</strong><br/>
        Several technical indicators are bullish, but not all are aligned. 
        There's reasonable confidence for an upward move, but with some caution.<br/><br/>
        
        <strong>Typical characteristics:</strong><br/>
        • Some bullish indicators present<br/>
        • Moderate momentum<br/>
        • Price may be consolidating<br/>
        • Moderate confidence (50-70%)
    """
    
    const val HOLD = """
        <strong>Hold (Score: -29 to 29)</strong><br/><br/>
        
        Neutral signal with mixed indicators or low conviction.<br/><br/>
        
        <strong>What this means:</strong><br/>
        Indicators are giving conflicting signals or showing no clear direction. 
        Neither bullish nor bearish conditions are dominant.<br/><br/>
        
        <strong>Typical characteristics:</strong><br/>
        • Mixed indicator readings<br/>
        • Price in consolidation or range<br/>
        • Uncertainty in trend direction<br/>
        • Low to moderate confidence
    """
    
    const val SELL = """
        <strong>Sell (Score: -59 to -30)</strong><br/><br/>
        
        Moderate sell signal with some negative indicators.<br/><br/>
        
        <strong>What this means:</strong><br/>
        Several technical indicators are bearish. There's reasonable concern 
        for downward pressure, though not all indicators are aligned.<br/><br/>
        
        <strong>Typical characteristics:</strong><br/>
        • Some bearish indicators present<br/>
        • Weakening momentum<br/>
        • Price may be breaking down<br/>
        • Moderate confidence (50-70%)
    """
    
    const val STRONG_SELL = """
        <strong>Strong Sell (Score: -100 to -60)</strong><br/><br/>
        
        High confidence sell signal with multiple negative indicators aligned.<br/><br/>
        
        <strong>What this means:</strong><br/>
        Multiple technical indicators are showing bearish signals. The stock has 
        negative momentum, unfavorable trend direction, and concerning patterns.<br/><br/>
        
        <strong>Typical characteristics:</strong><br/>
        • RSI showing weakness or oversold<br/>
        • MACD trending negative<br/>
        • Price below key moving averages<br/>
        • High selling volume<br/>
        • High confidence score (70%+)
    """
    
    const val SCORE_CALCULATION = """
        <strong>How Scores are Calculated</strong><br/><br/>
        
        The signal score is an aggregate of multiple technical indicators, 
        weighted by their reliability and current market conditions.<br/><br/>
        
        <strong>Components:</strong><br/>
        • RSI (momentum)<br/>
        • MACD (trend and momentum)<br/>
        • Moving averages (trend)<br/>
        • Volume patterns (confirmation)<br/>
        • Volatility measures (risk)<br/><br/>
        
        <strong>AI vs Rule-Based:</strong><br/>
        • <strong>Rule-Based Score:</strong> Traditional technical analysis formulas<br/>
        • <strong>AI Score:</strong> LLM analysis considering context, patterns, 
        and relationships between indicators<br/><br/>
        
        The AI score may differ from rule-based scores when it detects nuanced 
        patterns or market contexts that simple rules miss.
    """
    
    const val CONFIDENCE = """
        <strong>Confidence Score</strong><br/><br/>
        
        Measures how much the indicators agree and how reliable the signal is.<br/><br/>
        
        <strong>What affects confidence:</strong><br/>
        • <strong>Indicator Agreement:</strong> Do all indicators point the same way?<br/>
        • <strong>Volatility:</strong> Lower volatility = higher confidence<br/>
        • <strong>Pattern Clarity:</strong> Clear trends = higher confidence<br/>
        • <strong>Volume Confirmation:</strong> Strong volume = higher confidence<br/><br/>
        
        <strong>Ranges:</strong><br/>
        • 70-100%: High confidence<br/>
        • 50-69%: Moderate confidence<br/>
        • 0-49%: Low confidence<br/><br/>
        
        Higher confidence doesn't guarantee success, but indicates clearer signals.
    """
}
