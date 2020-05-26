import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

public class Test {

   public static void main(String[] args) {

      Object ds =
            "{appliance={backup_schedules=[{backup_password=123456, enable=true, location=http://myurl.com, location_password=123456, location_user=/home/test/, parts=[value1, value2], recurrence_info={days=[MONDAY, SUNDAY], hour=1, minute=1}, retention_info={max_count=1}, schedule_id=1}, {backup_password=123456, enable=true, location=http://myurl.com, location_password=123456, location_user=/home/test/, parts=[value1, value2], recurrence_info={days=[MONDAY, SATURDAY], hour=1, minute=1}, retention_info={max_count=1}, schedule_id=1}], consoleCli={enabled=true}, dcui={enabled=true}, local_accounts_policy={max_days=10, min_days=5, warn_days=2}, network={dns_server_configuration={mode=DHCP, servers=[198.0.0.1, 198.0.0.2]}, firewall_rule_policies=[{address=198.0.0.1, interface_name=NAC, policy=IGNORE, prefix=1}, {address=198.0.0.2, interface_name=NAC, policy=IGNORE, prefix=1}], proxy_configuration=[{enabled=true, password=123456, port=1, protocol=HTTP, server=198.0.0.1, username=test}, {enabled=true, password=123456, port=1, protocol=HTTP, server=198.0.0.2, username=test2}]}, ntp={servers=[198.0.0.1, 198.0.0.2]}, root_local_account={email=dummy@vmware.com, enabled=true, fullname=Admin, has_password=true, last_password_change=2015-01-01T22:13:05.651Z, max_days_between_password_change=1, min_days_between_password_change=1, password=123456, password_expires_at=2015-01-01T22:13:05.651Z, roles=[Backup, Restore], warn_days_before_password_expiration=1}, shell={enabled=true, timeout=1}, smtp={mail_server=map://dummy.com, relay_port=1234}, software_update_policy={auto_stage=ENABLED, customURL=http://c.com, day=MONDAY, defaultURL=http://d.com, hour=20, minute=59, password=1234567}, ssh={enabled=false}, syslog=[{hostname=198.0.0.1, port=1, protocol=TLS}, {hostname=198.0.0.2, port=1, protocol=UDP}], time_sync={mode=NTP}, time_zone={name=IST}}}";
      Gson g = new Gson();
      ObjectMapper mapper = new ObjectMapper();
      JSONObject jo = mapper.convertValue(ds, JSONObject.class);
      System.out.println(jo);
      //ApplianceManagement p = g.fromJson(jo.get("appliance"), ApplianceManagement.class);

      //System.out.println("done " + p);

   }
}