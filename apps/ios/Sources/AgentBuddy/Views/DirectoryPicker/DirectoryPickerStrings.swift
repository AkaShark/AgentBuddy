import Foundation

enum DirectoryPickerStrings {
    static let title = String(localized: "directory_picker_title")
    static let changeServer = String(localized: "directory_picker_change_server")
    static let searchFolders = String(localized: "directory_picker_search_folders")
    static let upOneLevel = String(localized: "directory_picker_up_one_level")
    static let loadError = String(localized: "directory_picker_load_error")
    static let retry = String(localized: "directory_picker_retry")
    static let recentDirectories = String(localized: "directory_picker_recent_directories")
    static let clearRecentDirectories = String(localized: "directory_picker_clear_recent_directories")
    static let recentFooter = String(localized: "directory_picker_recent_footer")
    static let noSubdirectories = String(localized: "directory_picker_no_subdirectories")
    static let chooseFolderHelper = String(localized: "directory_picker_choose_folder_helper")
    static let selectFolder = String(localized: "directory_picker_select_folder")
    static let cancel = String(localized: "directory_picker_cancel")
    static let clearRecentTitle = String(localized: "directory_picker_clear_recent_title")
    static let clearRecentMessage = String(localized: "directory_picker_clear_recent_message")
    static let clear = String(localized: "directory_picker_clear")
    static let noServerSelected = String(localized: "directory_picker_no_server_selected")
    static let serverNotConnected = String(localized: "directory_picker_server_not_connected")
    static let newFolder = String(localized: "directory_picker_new_folder")
    static let newFolderTitle = String(localized: "directory_picker_new_folder_title")
    static let newFolderPlaceholder = String(localized: "directory_picker_new_folder_placeholder")
    static let create = String(localized: "directory_picker_create")
    static let createFolderFailed = String(localized: "directory_picker_create_folder_failed")
    static let goToPath = String(localized: "directory_picker_go_to_path")
    static let goToPathTitle = String(localized: "directory_picker_go_to_path_title")
    static let pathPlaceholder = String(localized: "directory_picker_path_placeholder")
    static let go = String(localized: "directory_picker_go")

    static func connectedServer(_ label: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_connected_server"), label)
    }

    static func noMatches(_ query: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_no_matches"), query)
    }

    static func continueIn(_ folder: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_continue_in_folder"), folder)
    }

}
