import 'package:shared_preferences/shared_preferences.dart';

/// Wraps SharedPreferences access for the saved server list,
/// the last used url and the auto-connect setting.
class ServerStore {
  static const _serversKey     = 'servers';
  static const _lastUrlKey     = 'lastUrl';
  static const _autoConnectKey = 'autoConnect';
  static const _legacyUrlKey   = 'url'; // single-url key used by 1.0.x

  final SharedPreferences _prefs;

  ServerStore(this._prefs);

  static Future<ServerStore> load() async {
    final prefs = await SharedPreferences.getInstance();
    final store = ServerStore(prefs);
    await store._migrateLegacyUrl();
    return store;
  }

  /// 1.0.x stored a single url under 'url' - seed the server list with it.
  Future<void> _migrateLegacyUrl() async {
    final legacy = _prefs.getString(_legacyUrlKey);
    if (legacy != null && servers.isEmpty) {
      await addServer(legacy);
      await setLastUrl(legacy);
    }
  }

  List<String> get servers     => _prefs.getStringList(_serversKey) ?? [];
  String?      get lastUrl     => _prefs.getString(_lastUrlKey);
  bool         get autoConnect => _prefs.getBool(_autoConnectKey) ?? false;

  Future<void> addServer(String url) async {
    final list = servers;
    if (!list.contains(url)) {
      list.add(url);
      await _prefs.setStringList(_serversKey, list);
    }
  }

  Future<void> removeServer(String url) async {
    final list = servers..remove(url);
    await _prefs.setStringList(_serversKey, list);
  }

  Future<void> clearServers()          => _prefs.setStringList(_serversKey, []);
  Future<void> setLastUrl(String url)  => _prefs.setString(_lastUrlKey, url);
  Future<void> setAutoConnect(bool on) => _prefs.setBool(_autoConnectKey, on);
}
