package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.SpaceAttributes

class UseSpaces: AccessAction<SpaceAttributes>(USE_SPACES_ACTION_NAME){

    companion object {
        const val USE_SPACES_ACTION_NAME = "useSpaces"
    }
}

class ManageSpaces: AccessAction<SpaceAttributes>(MANAGE_SPACES_ACTION_NAME){

    companion object {
        const val MANAGE_SPACES_ACTION_NAME = "manageSpaces"
    }
}
