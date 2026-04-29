import requests
import pandas as pd
import numpy as np
import time

symbols = ['BTC', 'ETH', 'XRP', 'LTC', 'BCH', 'MONA']
# 💡【修正】W1（週足）をリストに追加しました
timeframes = [
    ('M5', 'histominute', 5, 5),
    ('M15', 'histominute', 15, 15),
    ('M30', 'histominute', 30, 30),
    ('H1', 'histohour', 1, 60),
    ('H4', 'histohour', 4, 240),
    ('D1', 'histoday', 1, 1440),
    ('W1', 'histoday', 7, 10080)
]

all_dfs = []
print("🌐 過去数年分・全時間足の超巨大データを取得開始します（数分かかります）...")

for sym in symbols:
    for tf_name, endpoint, agg, tf_id in timeframes:
        print(f"⏳ {sym} - {tf_name} のデータをページネーション取得中...")
        
        raw_data = []
        to_ts = int(time.time())
        
        # 5ページ分遡る (2000本 × 5回 = 約1万本を取得)
        for _ in range(5):
            url = f"https://min-api.cryptocompare.com/data/v2/{endpoint}?fsym={sym}&tsym=JPY&limit=2000&aggregate={agg}&toTs={to_ts}"
            try:
                res = requests.get(url).json()
                if res['Response'] == 'Success' and len(res['Data']['Data']) > 0:
                    raw_data.extend(res['Data']['Data'])
                    to_ts = res['Data']['TimeFrom'] - 1 
                time.sleep(0.1) 
            except: pass

        if not raw_data: continue

        df = pd.DataFrame(raw_data).drop_duplicates('time').sort_values('time')

        # 指標計算
        delta = df['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(14).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(14).mean()
        df['RSI'] = 100 - (100 / (1 + gain / loss))
        ma20 = df['close'].rolling(20).mean()
        df['MADev'] = ((df['close'] - ma20) / ma20) * 100
        ma5 = df['close'].rolling(5).mean()
        ma25 = df['close'].rolling(25).mean()
        df['F4'] = ((ma5 - ma25) / ma25) * 100  
        df['F5'] = (abs(df['close'] - df['open']) / df['close']) * 100  
        df['F6'] = (df['high'] - df[['open', 'close']].max(axis=1)) / df['close'] * 100  
        df['F7'] = (df[['open', 'close']].min(axis=1) - df['low']) / df['close'] * 100  
        df['StdDevPct'] = (df['close'].rolling(20).std() / df['close']) * 100
        df['VolRatio'] = (df['volumeto'] / df['volumeto'].shift(1)) * 100
        
        df['TimeframeID'] = tf_id
        df['Target'] = (df['close'].shift(-1) > df['close']).astype(int)

        df = df.dropna()
        feature_cols = ['RSI', 'StdDevPct', 'MADev', 'F4', 'F5', 'F6', 'F7', 'VolRatio', 'TimeframeID', 'Target']
        all_dfs.append(df[feature_cols])

master_df = pd.concat(all_dfs, ignore_index=True).replace([np.inf, -np.inf], np.nan).dropna()
master_df.to_csv('training_data.csv', index=False)
print(f"✅ 完了！合計 {len(master_df)} 件のマルチタイムフレーム学習データが完成しました！")