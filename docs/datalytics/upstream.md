# Datalytics Upstream Messages

### App Details Message

|Key|Type|Description|
|----|----|----|
|package_name|String?||
|app_version|String?||
|src|String?|Installer|
|fit|String|First Install Time|
|lut|String|Last Update Time|
|app_name|String?||
|sign|String?|Signature|
|hidden_app|String||



### App Is Hidden Message

|Key|Type|Description|
|----|----|----|
|hidden_app|Boolean||



### Cell Info Message

|Key|Type|Description|
|----|----|----|
|cellsInfo|List<CellArray>||

#### Cell Array LTE

|Key|Type|Description|
|----|----|----|
|registered|Boolean||
|CellIdentityLte|CellLTE||
|CellSignalStrengthLte|SSP||

#### Cell LTE

|Key|Type|Description|
|----|----|----|
|ci|Int?||
|mcc|Int?||
|mnc|Int?||
|pci|Int?||
|tac|Int?||

#### Cell Array CDMA

|Key|Type|Description|
|----|----|----|
|registered|Boolean||
|CellIdentityCdma|CellCDMA||
|CellSignalStrengthCdma|SSP||


#### Cell CDMA

|Key|Type|Description|
|----|----|----|
|basestationId|Int?||
|latitude|Int?||
|longitude|Int?||
|networkId|Int?||
|systemId|Int?||

#### Cell Array WCDMA

|Key|Type|Description|
|----|----|----|
|registered|Boolean||
|CellIdentityWcmda|CellWCDMA||
|CellSignalStrengthWcmda|SSP||

#### Cell WCDMA

|Key|Type|Description|
|----|----|----|
|cid|Int?||
|mcc|Int?||
|mnc|Int?||
|psc|Int?||
|lac|Int?||

#### Cell Array GSM

|Key|Type|Description|
|----|----|----|
|registered|Boolean||
|CellIdentityGsm|CellGSM||
|CellSignalStrengthGsm|SSP||

#### Cell GSM

|Key|Type|Description|
|----|----|----|
|cid|Int?||
|mcc|Int?||
|mnc|Int?||
|lac|Int?||

#### SSP

|Key|Type|Description|
|----|----|----|
|dbm|Int?||
|level|Int||
|original|String||
|timingAdvance|Int = 0||



### Constant Data Message

|Key|Type|Description|
|----|----|----|
|brand|String||
|model|String||
|screen|String||



### Floating Data Message

|Key|Type|Description|
|----|----|----|
|lat|String?||
|long|String?||
|ip|String?||
|type|NetworkType?||
|ssid|String?||
|sig_level|Int?||
|network|String?||



### Variable Data Message

|Key|Type|Description|
|----|----|----|
|os_version|String||
|app_version|String||
|av_code|String|App Version Code|
|pushe_version|String||
|pv_code|String|Pushe Version Code|
|gplay_version|String?||
|operator|String?||
|operator_2|String?||
|installer|String?||
|ad_id|String?||



### Wifi Info Message

|Key|Type|Description|
|----|----|----|
|ssid|String||
|mac|String||
|sig_level|Int||
|lat|String||
|long|String||