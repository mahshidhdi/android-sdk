# Notifications

### Application Download Message


|Key|Type|Description|
|----|----|----|
|orig_msg_id|String||
|package_name|String?||



### Application Install Message

|Key|Type|Description|
|----|----|----|
|orig_msg_id|String||
|status|InstallStatus||
|prev_version|String?||
|app_info|ApplicationDetail?||



### Notification Action Message

Sent when a notification or notification button has been clicked

|Key|Type|Description|
|----|----|----|
|orig_msg_id|String||
|status|NotificationResponseAction||
|btn_id|Int?||
|internet|String?|Wifi or Cellular|
|network|String?|Mobile Network Name|



### Notification Report Message

Sent when a notification is published or could not be published because notifications are disabled

|Key|Type|Description|
|----|----|----|
|orig_msg_id|String||
|status|Int||
|internet|String|Wifi or Cellular|
|network|String?|Mobile network name|



### User Input Data Message

Sent when a the user submits data in a notification dialog

|Key|Type|Description|
|----|----|----|
|orig_msg_id|String||
|data|Map<String||