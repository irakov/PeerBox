#pragma once

#include <map>
#include <string>

// resource id representing no icon, i.e. command without icon
#define NO_ICON 0


enum CommandId {
	CMD_TOP,
	CMD_DELETE,
	CMD_VERSIONS,
	CMD_SHARE,
};

struct CommandInfo {
	// a unique id in the CommandId enum
	CommandId id;
	// text displayed in context menu
	std::wstring menuText;
	// help text displayed in bottom status bar
	std::wstring helpText;
	// id to a bitmap resource (in resource.h)
	int bitmapResourceId;

};

class Command
{
public:
	static std::map<CommandId, CommandInfo> CreateCommandMap();
	static bool hasIcon(struct CommandInfo *cmd);
};
