#!/usr/bin/env python3
"""Fix all remaining Chief->Speaker references in Java files."""

import re
import os

FILES = [
    'src/main/java/de/ajsch/villagerai/service/ChiefService.java',
    'src/main/java/de/ajsch/villagerai/service/QuestService.java',
    'src/main/java/de/ajsch/villagerai/command/ChiefCommand.java',
    'src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java',
]

for path in FILES:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Replace .chatName() with .displayName()
    content = content.replace('.chatName()', '.displayName()')
    
    # Replace chiefId references (already done via bulk, but double-check)
    content = content.replace('.chiefId()', '.speakerId()')
    
    # For files that use Speaker parameter (QuestService, ChiefCommand) replace Chief type in signatures
    if 'QuestService' in path:
        # Replace 'Chief chief' parameter with 'Speaker speaker' in method signatures
        content = re.sub(r'Chief\s+speaker\b', r'Speaker speaker', content)
        # Replace references like chief.speakerId() to speaker.speakerId()
        content = content.replace('chief.', 'speaker.')
        # Fix any remaining 'Chief chief' patterns
        content = content.replace('Chief chief', 'Speaker speaker')
    
    if 'ChiefService' in path:
        # Replace broadcast methods with chatName->displayName already done, 
        # but same for other speaker references
        content = content.replace('speaker.chatName()', 'speaker.displayName()')
    
    if 'ChiefCommand' in path:
        # Already applied fixes; but ensure speaker.displayName() and remove leftover chief references
        content = content.replace('chief.displayName()', 'speaker.displayName()')
        content = content.replace('chief.speakerId()', 'speaker.speakerId()')
        content = content.replace('chief.villageId()', 'speaker.villageId()')
        content = content.replace('chief.villageName()', 'speaker.villageName()')
        content = content.replace('chief.role()', 'speaker.role()')
        content = content.replace('chief.personality()', 'speaker.personality()')
        content = content.replace('chief.greeting()', 'speaker.greeting()')
        content = content.replace('chief.entityUuid()', 'speaker.entityUuid()')
        content = content.replace('chief.world()', 'speaker.world()')
        content = content.replace('chief.x()', 'speaker.x()')
        content = content.replace('chief.y()', 'speaker.y()')
        content = content.replace('chief.z()', 'speaker.z()')
    
    if 'VillageChiefPlugin' in path:
        # Fix ChiefService constructor call: replace villagerProfileRepository with villageIdentityService, 
        # add speakerService and mourningService
        # The old call: new ChiefService(keys, chiefRepository, villagerProfileRepository, villageIdentityService, chiefVisualService, getLogger(), dataLoader.loadChiefProfilesSection());
        old = ('        this.chiefService = new ChiefService(\n'
               '            keys,\n'
               '            chiefRepository,\n'
               '            villagerProfileRepository,\n'
               '            villageIdentityService,\n'
               '            chiefVisualService,\n'
               '            getLogger(),\n'
               '            dataLoader.loadChiefProfilesSection());')
        new = ('        this.chiefService = new ChiefService(\n'
               '            keys,\n'
               '            chiefRepository,\n'
               '            villageIdentityService,\n'
               '            chiefVisualService,\n'
               '            speakerService,\n'
               '            mourningService,\n'
               '            getLogger());')
        content = content.replace(old, new)
        
        # Add SpeakerRepository and SpeakerService imports if missing
        if 'import de.ajsch.villagerai.storage.SpeakerRepository;' not in content:
            content = content.replace(
                'import de.ajsch.villagerai.storage.ChiefRepository;',
                'import de.ajsch.villagerai.storage.ChiefRepository;\nimport de.ajsch.villagerai.storage.SpeakerRepository;\nimport de.ajsch.villagerai.storage.YamlSpeakerRepository;'
            )
        if 'import de.ajsch.villagerai.service.SpeakerService;' not in content:
            content = content.replace(
                'import de.ajsch.villagerai.service.ChiefService;',
                'import de.ajsch.villagerai.service.ChiefService;\nimport de.ajsch.villagerai.service.SpeakerService;'
            )
        
        # Add fields
        if 'private SpeakerRepository speakerRepository;' not in content:
            content = content.replace(
                '    private ChiefService chiefService;',
                '    private SpeakerRepository speakerRepository;\n    private SpeakerService speakerService;\n    private ChiefService chiefService;'
            )
        
        # Add init of speakerRepository and speakerService after villageIdentityService setup
        init_block = ('this.villageIdentityService.setVillageRepository(new YamlVillageRepository(this));\n'
                      '        this.villageIdentityService.setLogger(getLogger());\n'
                      '        this.speakerRepository = new YamlSpeakerRepository(this);\n'
                      '        this.speakerService = new SpeakerService(this, speakerRepository, villageIdentityService, getLogger());')
        content = content.replace(
            'this.villageIdentityService.setVillageRepository(new YamlVillageRepository(this));\n        this.villageIdentityService.setLogger(getLogger());',
            init_block
        )
        
        # Fix registerListeners: VillagerInteractListener expects SpeakerService, not ChiefService
        content = content.replace(
            'new VillagerInteractListener(\n                chiefService,',
            'new VillagerInteractListener(\n                speakerService,'
        )
        # Fix QuestLifecycleListener
        content = content.replace(
            'new QuestLifecycleListener(\n            this,\n            chiefService,',
            'new QuestLifecycleListener(\n            this,\n            speakerService,'
        )
        # Fix VillagerProfileListener
        content = content.replace(
            'new VillagerProfileListener(chiefService)',
            'new VillagerProfileListener(speakerService)'
        )
        # Fix registerCommands: add speakerService param
        content = content.replace(
            'new ChiefCommand(\n            this,\n            chiefService,',
            'new ChiefCommand(\n            this,\n            chiefService,\n            speakerService,'
        )
        # Fix refreshLoadedVillagerProfiles call
        content = content.replace(
            'chiefService.refreshLoadedVillagerProfiles(villagers)',
            'speakerService.refreshLoadedVillagerProfiles(villagers)'
        )
        # Fix chiefService.reloadProfiles -> currently SpeakerService doesn't have reloadProfiles, keep as chiefService if still exists
        # (We'll check later)
    
    
    if content != original:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'Fixed {path}')
    else:
        print(f'No changes for {path}')