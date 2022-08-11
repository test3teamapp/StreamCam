export IFNAME="wlan0"
export CON_NAME="myhotspot"
nmcli con add type wifi ifname $IFNAME con-name $CON_NAME autoconnect yes ssid $CON_NAME
nmcli con modify $CON_NAME 802-11-wireless.mode ap 802-11-wireless.band bg ipv4.method shared
nmcli con modify $CON_NAME wifi-sec.key-mgmt wpa-psk
nmcli con modify $CON_NAME wifi-sec.psk "MyStrongHotspotPass"
nmcli con up $CON_NAME
nmcli connection show $CON_NAME

Connection successfully activated (D-Bus active path: /org/freedesktop/NetworkManager/ActiveConnection/7)
