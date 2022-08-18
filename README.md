# idp
Open ID based Identity provider for large scale authentication.


//key_alias
//key_store
//key_policy_def -- ROOT (5) and IDP_SERVICE (3)
//

1. create idp-core library
2. rename method names in authwrapper interface
3. internal -- Need to rename
4. provide separate request mapping in each controller
5. rename dto with full form
6. 



ACR:

level 1 | OTP
level 2 | 1 Finger
level 3 | 2 Fingers
level 4 | 10 Fingers
level 5 | iris
level 6 | OTP + 1 Finger
level 7 | OTP + 2 Finger
level 8 | OTP + 10 Fingers
level 9 | OTP + iris
level 10| OTP + 1 Finger + iris
level 11| OTP + 2 Finger + iris
level 12| OTP + 10 Finger + iris


level 1 | OTP
level 2 | 1 Finger
level 3 | 2 Fingers || OTP + 1 finger
level 4 | OTP + 10 Finger || IRIS
level 5 | IRIS + OTP


order of precedence
acr -- level 4, level 3

( OTP && 10 Finger ) || IRIS
1 Finger


auth factors to be sent to UI

[[OTP, 10 Finger], [IRIS], [2 Fingers], [OTP, 1 Finger]]





