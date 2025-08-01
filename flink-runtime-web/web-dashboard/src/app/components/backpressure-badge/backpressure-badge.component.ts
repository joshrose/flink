/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { UpperCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

const colorMap: Record<string, string> = {
  'in-progress': '#f5222d',
  ok: '#52c41a',
  low: '#faad14',
  high: '#f5222d'
};

@Component({
  selector: 'flink-backpressure-badge',
  templateUrl: './backpressure-badge.component.html',
  styleUrls: ['./backpressure-badge.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UpperCasePipe]
})
export class BackpressureBadgeComponent {
  @Input() public state: string;

  public get backgroundColor(): string {
    return colorMap[this.state?.toLowerCase()];
  }
}
