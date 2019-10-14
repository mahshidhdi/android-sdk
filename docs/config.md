# Pushe SDK Configuration Values

##### Core
- **fcm_disabled** : Boolean

##### Notification
- **notif_disabled** : Boolean

##### Datalytics
- There is an `CollectorSettings` object stored as a string for each data collection
    **"collectable_${messageType}"** : CollectorSettings
    
    CollectorSettings: 
    """{
        'repeatInterval': Long,
        'sendPriority': 'whenever' | 'buffer' | 'soon' | 'immediate'
    }"""

##### Analytics
- **session_pause_limit** : Long
- **session_fragment_flow_enabled** : Boolean
- **session_fragment_flow_depth_limit** : Int
- **session_fragment_flow_exception_list** : List of `FragmentFlowInfo`
    FragmentFlowInfo:
    """{
        'activity_name': String,
        'fragment_id': String
    }"""
