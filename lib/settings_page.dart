import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import './login_page.dart' show AppColors;
import './server_store.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.store});

  final ServerStore store;

  @override
  SettingsPageState createState() => SettingsPageState();
}

enum SettingsSection { autoConnect, clearServers }

class SettingsPageState extends State<SettingsPage> {
  final FocusNode _screenFocusNode = FocusNode();

  SettingsSection _section = SettingsSection.autoConnect;

  @override
  void initState() {
    super.initState();
    _screenFocusNode.requestFocus();
  }

  @override
  void dispose() {
    _screenFocusNode.dispose();
    super.dispose();
  }

  KeyEventResult _handleKeyEvent(KeyEvent event, BuildContext context) {
    if (!_screenFocusNode.hasFocus ||
        (event is! KeyDownEvent && event is! KeyRepeatEvent)) {
      return KeyEventResult.ignored;
    }

    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.arrowUp) {
      _navigate(-1);
    } else if (key == LogicalKeyboardKey.arrowDown) {
      _navigate(1);
    } else if (key == LogicalKeyboardKey.enter ||
               key == LogicalKeyboardKey.select) {
      _handleSelect(context);
    } else {
      return KeyEventResult.ignored;
    }
    return KeyEventResult.handled;
  }

  void _navigate(int delta) {
    final nextIndex = SettingsSection.values.indexOf(_section) + delta;
    if (nextIndex >= 0 && nextIndex < SettingsSection.values.length) {
      setState(() => _section = SettingsSection.values[nextIndex]);
    }
  }

  void _handleSelect(BuildContext context) {
    switch (_section) {
      case SettingsSection.autoConnect:
        _toggleAutoConnect();
        break;
      case SettingsSection.clearServers:
        _clearServers(context);
        break;
    }
  }

  void _toggleAutoConnect() {
    widget.store.setAutoConnect(!widget.store.autoConnect);
    setState(() {});
  }

  void _clearServers(BuildContext context) {
    widget.store.clearServers();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Saved servers cleared')),
    );
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: _screenFocusNode,
      onKeyEvent: (_, event) => _handleKeyEvent(event, context),
      child: Scaffold(
        appBar: AppBar(title: const Text('Settings')),
        backgroundColor: AppColors.bgPrimary,
        body: Padding(
          padding: const EdgeInsets.all(30.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _settingRow(
                focused: _section == SettingsSection.autoConnect,
                onTap: _toggleAutoConnect,
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Auto-connect to last server on startup',
                        style: TextStyle(color: AppColors.textPrimary, fontSize: 16),
                      ),
                    ),
                    Switch(
                      value: widget.store.autoConnect,
                      activeThumbColor: AppColors.accent,
                      onChanged: (_) => _toggleAutoConnect(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              _settingRow(
                focused: _section == SettingsSection.clearServers,
                onTap: () => _clearServers(context),
                child: Row(
                  children: [
                    Icon(Icons.delete_outline, color: AppColors.error),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Clear saved servers (${widget.store.servers.length})',
                        style: TextStyle(color: AppColors.textPrimary, fontSize: 16),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Text(
                'Press Back to return to the connection screen',
                style: TextStyle(color: AppColors.textMuted, fontSize: 13),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _settingRow({required bool focused, required VoidCallback onTap, required Widget child}) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: focused ? AppColors.bgActive : AppColors.bgTertiary,
          border: Border.all(
            width: 2,
            color: focused ? AppColors.accent : AppColors.border,
          ),
          borderRadius: BorderRadius.circular(10),
        ),
        child: child,
      ),
    );
  }
}
