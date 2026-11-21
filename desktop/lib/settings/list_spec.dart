import '../ui/fields.dart' show FieldInput;

export '../ui/fields.dart' show FieldInput;

// Variant В of docs/ui/platforms.md: a list screen of shared/screens, described once and drawn here and on the phone
// (app/src/main/java/io/niko/ui/settings/ListScreen.kt). shared/tool/gen_screens.py writes the descriptions as
// ListScreens (lib/gen/screens.g.dart); the texts are names in its screenTexts. docs/research/list-screens-probe.md.

/// A list screen: its name, the way back's label, its lines top to bottom.
class ListSpec {
  const ListSpec({required this.title, required this.back, required this.lines});

  final String title;
  final String back;
  final List<Line> lines;
}

/// One part of the page.
sealed class Line {
  const Line();
}

/// Fields side by side (m-fields-row).
class FieldsLine extends Line {
  const FieldsLine(this.fields);

  final List<FieldSpec> fields;
}

/// One field across the page (m-field).
class FieldLine extends Line {
  const FieldLine(this.field);

  final FieldSpec field;
}

/// Rows in a group of their own (m-list).
class GroupLine extends Line {
  const GroupLine(this.items);

  final List<Item> items;
}

/// Rows under an overline (m-section).
class SectionLine extends Line {
  const SectionLine(this.title, this.items);

  final String title;
  final List<Item> items;
}

/// A row of a group.
sealed class Item {
  const Item();
}

/// A switch of the setting [key]; [enabledBy] the switch it needs on.
class SwitchItem extends Item {
  const SwitchItem(this.key, this.title, {this.enabledBy});

  final String key;
  final String title;
  final String? enabledBy;
}

/// A row that opens something: the host's summary of it (SettingValues.summary) and a chevron; pressed, onAction(id).
class LinkItem extends Item {
  const LinkItem(this.id, this.title);

  final String id;
  final String title;
}

/// A field of the setting [key]; [action] an icon button at its end, which the screen's host answers by its id.
class FieldSpec {
  const FieldSpec(this.key, this.label, {this.input = FieldInput.text, this.mono = false, this.narrow = false, this.action});

  final String key;
  final String label;
  final FieldInput input;
  final bool mono;
  final bool narrow;
  final FieldAction? action;
}

class FieldAction {
  const FieldAction(this.id, this.icon, this.label);

  final String id;

  /// The icon's path data (NikoIcons).
  final String icon;
  final String label;
}
