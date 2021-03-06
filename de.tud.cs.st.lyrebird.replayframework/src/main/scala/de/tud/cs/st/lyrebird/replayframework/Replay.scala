/* License (BSD Style License):
 * Copyright (c) 2011
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  - Neither the name of the Software Technology Group or Technische 
 *    Universität Darmstadt nor the names of its contributors may be used to 
 *    endorse or promote products derived from this software without specific 
 *    prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package de.tud.cs.st.lyrebird.replayframework

import java.io.File
import de.tud.cs.st.lyrebird.replayframework.file.Reader

/**
 * Central entry point to lyrebird recorder.
 * These class handles to replay previous recorded events by Lyrebird.recorder
 *
 * @param location : default packed folder in the output folder of lyrebird recorder
 * @author Malte Viering
 */
class Replay(val allEventSets : List[EventSet]) {
        
    def this(location: File) {
        this(new Reader(location).getAllEventSets())
    }

    // TODO this code smells: why is there not fChanged?? There is no explanation whatsoever... no examples...
    def processAllEventSets(fAdd: File ⇒ _, fRemove: File ⇒ _) {
        allEventSets.foreach(processEventSet(_, fAdd, fRemove))
    }
    
    // TODO this code smells: why is there not fChanged? There is no explanation whatsoever... no examples...
    def processEventSet(eventSet: EventSet, fAdd: File ⇒ _, fRemove: File ⇒ _) {
        eventSet.eventFiles.foreach(x ⇒ {
            x match {
                case Event(EventType.ADDED, _, _, file, _) ⇒ fAdd(file)
                case Event(EventType.REMOVED, _, _, file, Some(prev)) ⇒
                    if (prev.eventType != EventType.REMOVED) { 
                        fRemove(prev.eventFile) 
                     }
                case Event(EventType.REMOVED, _, _, file, None) ⇒ // do nothing
                case Event(EventType.CHANGED, _, _, file, Some(prev)) ⇒ {
                    if (prev.eventType != EventType.REMOVED) 
                        fRemove(prev.eventFile)
                    fAdd(file)
                }
                case Event(EventType.CHANGED, _, _, file, None) ⇒ {
                    fAdd(file)
                }
            }
        })
    }
}