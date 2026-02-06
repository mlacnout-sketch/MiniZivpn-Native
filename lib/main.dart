import 'package:flutter/material.dart';
import 'screens/home_page.dart';

void main() {
  runApp(const MiniZivpnApp());
}

class MiniZivpnApp extends StatelessWidget {
  const MiniZivpnApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'MiniZivpn',
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6C63FF),
          brightness: Brightness.dark,
          surface: const Color(0xFF1E1E2E),
        ),
        scaffoldBackgroundColor: const Color(0xFF121218),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.transparent,
          elevation: 0,
          centerTitle: true,
        ),
        cardTheme: CardThemeData(
          color: const Color(0xFF272736),
          elevation: 4,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        ),
      ),
      home: const HomePage(),
    );
  }
}