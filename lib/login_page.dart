import 'dart:async';
import 'package:custom_tv_text_field/custom_tv_text_field.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

import './server_store.dart';
import './settings_page.dart';
import './webview_page.dart';

class AppColors {
  //static final Color focusColor    = Colors.blue;
  //static final Color unfocusColor  = Colors.white;

  static final Color bgPrimary     = const Color(0xff0a0a0f);
  static final Color bgSecondary   = const Color(0xff12121a);
  static final Color bgTertiary    = const Color(0xff1a1a25);
  static final Color bgHover       = const Color(0xff22222f);
  static final Color bgActive      = const Color(0xff2a2a3a);
  static final Color textPrimary   = const Color(0xfff1f1f5);
  static final Color textSecondary = const Color(0xffa1a1aa);
  static final Color textMuted     = const Color(0xff71717a);
  static final Color accent        = const Color(0xff6366f1);
  static final Color accentHover   = const Color(0xff818cf8);
  static final Color success       = const Color(0xff10b981);
  static final Color warning       = const Color(0xfff59e0b);
  static final Color error         = const Color(0xffef4444);
  static final Color border        = const Color(0xff27272a);
  static final Color borderLight   = const Color(0xff3f3f46);

}

class LoginPage extends StatefulWidget {
  const LoginPage ({super.key, required this.store});

  final ServerStore store;
  @override
  LoginPageState createState() => LoginPageState();
}

/// Actions available on a saved-server row (toggled with left/right).
enum ServerRowAction { open, delete }

class LoginPageState extends State<LoginPage> {

  final FocusNode                         _screenFocusNode = FocusNode();
  final GlobalKey<FormState>              _formKey         = GlobalKey<FormState>();

  late  TextEditingController             _urlController;
  final GlobalKey<CustomTVTextFieldState> _urlKey          = GlobalKey<CustomTVTextFieldState>();

  // Focus sections, top to bottom:
  // 0 = url field, 1 = connect button, 2..2+n-1 = saved server rows, 2+n = settings button
  int             _section         = 0;
  ServerRowAction _rowAction       = ServerRowAction.open;
  bool            _hasKeyboardOpen = false;
  bool            _connecting      = false;

  List<String>    _servers         = [];

  int get _settingsSection => 2 + _servers.length;

  LoginPageState ();

  @override
  void initState() {
    super.initState();
    _servers       = widget.store.servers;
    _urlController = TextEditingController(text: widget.store.lastUrl ?? 'http://192.168.0.90:3000/');
    _screenFocusNode.requestFocus();

    final String? last = widget.store.lastUrl;
    if (widget.store.autoConnect && last != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Auto-connecting to $last'),),);
          _connectTo(context, last);
        }
      });
    }
  }

  @override
  void dispose() {
    _screenFocusNode.dispose();
    _urlController.dispose();
    super.dispose();
  }

  bool _canHandleKeys() => _screenFocusNode.hasFocus && !_hasKeyboardOpen;

  bool _onServerRow() => _section >= 2 && _section < _settingsSection;

  KeyEventResult _handleKeyEvent(KeyEvent event, BuildContext context) {
    if (!_canHandleKeys() ||
        (event is! KeyDownEvent && event is! KeyRepeatEvent)) {
      return KeyEventResult.ignored;
    }

    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.arrowUp) {
      _navigate(-1);
    } else if (key == LogicalKeyboardKey.arrowDown) {
      _navigate(1);
    } else if ((key == LogicalKeyboardKey.arrowLeft ||
                key == LogicalKeyboardKey.arrowRight) && _onServerRow()) {
      setState(() {
        _rowAction = _rowAction == ServerRowAction.open
          ? ServerRowAction.delete
          : ServerRowAction.open;
      });
    } else if (key == LogicalKeyboardKey.enter ||
               key == LogicalKeyboardKey.select) {
      _handleSelect(context);
    } else {
      return KeyEventResult.ignored;
    }
    return KeyEventResult.handled;
  }

  void _navigate(int delta) {
    final nextIndex = _section + delta;
    if (nextIndex >= 0 && nextIndex <= _settingsSection) {
      setState(() {
        _section   = nextIndex;
        _rowAction = ServerRowAction.open;
      });
    }
  }

  void _handleSelect(BuildContext context) {
    if (_section == 0) {
      _urlKey.currentState?.toggleKeyboard();
    } else if (_section == 1) {
      _submitConnect(context);
    } else if (_onServerRow()) {
      final String url = _servers[_section - 2];
      if (_rowAction == ServerRowAction.open) {
        _connectTo(context, url);
      } else {
        _deleteServer(context, url);
      }
    } else if (_section == _settingsSection) {
      _openSettings(context);
    }
  }

  void _deleteServer(BuildContext context, String url) async {
    await widget.store.removeServer(url);
    setState(() {
      _servers   = widget.store.servers;
      _rowAction = ServerRowAction.open;
      if (_section > _settingsSection) {
        _section = _settingsSection;
      }
    });
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Removed $url'),),);
    }
  }

  void _openSettings(BuildContext context) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => SettingsPage(store: widget.store)),
    );
    // servers may have been cleared in settings
    setState(() {
      _servers = widget.store.servers;
      if (_section > _settingsSection) {
        _section = _settingsSection;
      }
      _screenFocusNode.requestFocus();
    });
  }

  void _submitConnect(BuildContext context) {

    // basic validation
    final Uri?   uri = Uri.tryParse(_urlController.value.text);
    if (uri == null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Invalid url ${_urlController.value.text}'),),);
      return;
    }
    // stupid package expects a trailing slash for soem reason => append trailing slash
    if ( !uri.hasAbsolutePath && (!_urlController.value.text.endsWith('\\')) ) {
      _urlController.text = '${_urlController.value.text}/';
    }

    final String url = _urlController.value.text;
    if (_formKey.currentState?.validate() ?? false) {
      _connectTo(context, url);
    } else { // url failed validation
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Invalid url: $url'),),);
    }
  }

  void _connectTo(BuildContext context, String url) async {
    if (_connecting) {
      return;
    }
    _connecting = true;

    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Connecting to $url'),),);
    if (await validateUrl(url)) {

      // remember the server for the saved list and auto-connect
      await widget.store.addServer(url);
      await widget.store.setLastUrl(url);
      setState(() {
        _servers = widget.store.servers;
        _urlController.text = url;
      });

      if (context.mounted) {
        // open webview
        await Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => WebViewPage(uri: url,)
          ),
        );
      }
    } else { // error loading url
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error connecting to $url'),),);
      }
    }
    _connecting = false;
  }

  Future<bool> validateUrl (String url) async {
    try {
      final response = await http.get(Uri.parse(url)).timeout(Duration(seconds: 5));
      if (response.statusCode == 200) {
        // peek at html and validate if it is a valid nodecast tv url
        final String html = response.body;
        return ( (html.contains('nodecast-tv')) || (html.contains('NodeCast TV')) );
      }
      return false;
    }
    catch (ex) { // error connecting to url
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {

    return Focus (
      focusNode: _screenFocusNode,
      onKeyEvent: (_, event) => _handleKeyEvent(event, context),
      child:
        Scaffold(
          appBar: AppBar(title: const Text("Connect to nodecast-tv")),
          backgroundColor: AppColors.bgPrimary,
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(30.0),
              child: Form(
                key: _formKey,
                child:
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [

                      SizedBox(
                        width: double.infinity,
                        child: Text(
                          'nodecast-tv',
                          style: TextStyle(
                            fontSize: 32,
                            fontWeight: FontWeight.bold,
                            color: AppColors.accent,
                          ),
                          textAlign: TextAlign.center,
                        )
                      ),

                      SizedBox(
                        width: double.infinity,
                        height: 80,
                        child:
                          CustomTVTextField(
                            key: _urlKey,
                            controller: _urlController,
                            textStyle: TextStyle(color: AppColors.textPrimary),
                            hint: "Enter URL",
                            prefixIcon: const Icon(Icons.tv, color: Colors.white70),
                            isFocused: _section == 0 && !_hasKeyboardOpen,
                            onVisibilityChanged: (v) => setState(() => _hasKeyboardOpen = v),
                            onFieldSubmitted: (_) {}, // Removed unused onSubmitted parameter
                            keyboardType: KeyboardType.alphabetic,
                            backgroundColor: AppColors.bgTertiary,
                            focusedBorderColor: AppColors.accent,
                            borderColor: AppColors.border,
                            borderRadius: 10,
                            isRequired: true, // Removed unused validator parameter
                            textFieldType: TextFieldType.url,
                            maxLines: 1,
                          ),
                      ),

                      SizedBox(height: 16),

                      SizedBox(
                        width: double.infinity,
                        height: 49,
                        child:
                          ConnectButton(
                            isSelected: _section == 1 && !_hasKeyboardOpen,
                            onTap: _submitConnect,
                            context: context,
                          ),
                      ),

                      if (_servers.isNotEmpty) ...[
                        SizedBox(height: 24),
                        SizedBox(
                          width: double.infinity,
                          child: Text(
                            'Saved servers',
                            style: TextStyle(color: AppColors.textSecondary, fontSize: 14),
                          ),
                        ),
                        SizedBox(height: 8),
                        Flexible(
                          child: ListView.separated(
                            shrinkWrap: true,
                            itemCount: _servers.length,
                            separatorBuilder: (_, _) => SizedBox(height: 8),
                            itemBuilder: (context, i) => ServerRow(
                              url: _servers[i],
                              isFocused: _section == 2 + i && !_hasKeyboardOpen,
                              action: _rowAction,
                              onOpen: () => _connectTo(context, _servers[i]),
                              onDelete: () => _deleteServer(context, _servers[i]),
                            ),
                          ),
                        ),
                      ],

                      SizedBox(height: 24),

                      SizedBox(
                        width: double.infinity,
                        height: 49,
                        child: OutlinedButton.icon(
                          onPressed: () => _openSettings(context),
                          icon: Icon(Icons.settings, color: AppColors.textSecondary),
                          label: Text(
                            'Settings',
                            style: TextStyle(color: AppColors.textSecondary, fontSize: 16),
                          ),
                          style: OutlinedButton.styleFrom(
                            backgroundColor: _section == _settingsSection && !_hasKeyboardOpen
                              ? AppColors.bgActive
                              : Colors.transparent,
                            side: BorderSide(
                              width: 2,
                              color: _section == _settingsSection && !_hasKeyboardOpen
                                ? AppColors.accent
                                : AppColors.border,
                            ),
                          ),
                        ),
                      ),

                    ]
                  ),
              ),
            )
          )
        )
    );
  }

}

class ServerRow extends StatelessWidget {
  final String          url;
  final bool            isFocused;
  final ServerRowAction action;
  final VoidCallback    onOpen;
  final VoidCallback    onDelete;

  const ServerRow({super.key, required this.url, required this.isFocused, required this.action, required this.onOpen, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    final bool openSelected   = isFocused && action == ServerRowAction.open;
    final bool deleteSelected = isFocused && action == ServerRowAction.delete;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isFocused ? AppColors.bgActive : AppColors.bgTertiary,
        border: Border.all(width: 2, color: isFocused ? AppColors.accent : AppColors.border),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              url,
              style: TextStyle(color: AppColors.textPrimary, fontSize: 15),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          SizedBox(width: 8),
          _actionChip(
            icon: Icons.play_arrow,
            label: 'Open',
            selected: openSelected,
            color: AppColors.accent,
            onTap: onOpen,
          ),
          SizedBox(width: 8),
          _actionChip(
            icon: Icons.delete_outline,
            label: 'Delete',
            selected: deleteSelected,
            color: AppColors.error,
            onTap: onDelete,
          ),
        ],
      ),
    );
  }

  Widget _actionChip({required IconData icon, required String label, required bool selected, required Color color, required VoidCallback onTap}) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? color : Colors.transparent,
          border: Border.all(width: 1, color: selected ? color : AppColors.borderLight),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(icon, size: 16, color: selected ? Colors.white : AppColors.textSecondary),
            SizedBox(width: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: selected ? Colors.white : AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ConnectButton extends StatelessWidget {
  final bool isSelected;
  final Function(BuildContext) onTap;
  final BuildContext context;

  const ConnectButton({super.key, required this.isSelected, required this.onTap, required this.context});

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: () => onTap(context),
      style:
        ElevatedButton.styleFrom(
          backgroundColor: isSelected ? AppColors.accent : AppColors.accentHover,
          side: BorderSide(width: 2, color: isSelected ? AppColors.accent : AppColors.border,)
          ),
      //style: ButtonStyle(
      //  backgroundColor: WidgetStateProperty.all<Color>(AppColors.accent),),
      child: Text(
        'Open URL',
        style: TextStyle(
          color: Colors.white,
          fontSize: 16,
          fontWeight: FontWeight.bold
        ),
      ),
    );
  }
}
