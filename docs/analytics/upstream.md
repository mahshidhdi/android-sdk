# Analytics Upstream Messages

### Ecommerce Message

|Key|Type|Description|
|----|----|----|
|name|String||
|price|Double||
|category|String?||
|quantity|Long?||



### Event Message

|Key|Type|Description|
|----|----|----|
|name|String||
|action|String||



### Goal Reached Message

|Key|Type|Description|
|----|----|----|
|type|GoalType||
|name|String||
|view_goals|Map<String, String?>||
|view_goals_with_error|List<ViewGoal>||
|activity_funnel|List<String>||
|fragment_funnel|List<String>||



### Session Info Message

|Key|Type|Description|
|----|----|----|
|session_flow|List<SessionActivity>||
|app_version|String?||


#### Session Activity

|Key|Type|Description|
|----|----|----|
|name|String||
|start_time|Long||
|duration|Long||
|fragment_flows|Map<String, SessionFragment>||